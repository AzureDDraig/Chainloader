# Screen & Widget Inheritance Changes

Minecraft 1.21.1 introduced critical refactorings to screen rendering, GUI widgets, and the widget tick lifecycle. Many properties that were previously public/mutable became final, private, or encapsulation-protected. 

ChainLoader uses targeted ASM bytecode transformations to restore legacy widget inheritance behavior, allowing pre-1.21.1 screens and custom text inputs to compile and render correctly.

---

## 1. Removing final from renderWidget

In older Minecraft versions, subclasses of `AbstractWidget` (such as custom buttons, checkboxes, and text fields) drew their content by overriding `renderWidget(GuiGraphics, int, int, float)`. 

In 1.21.1, Mojang declared `renderWidget` as `final` inside `AbstractWidget` to enforce a new rendering pipeline, which causes legacy widgets to crash with `java.lang.VerifyError: Method overrides final method` during classloading.

To prevent this, ChainLoader's class transformer strips the `ACC_FINAL` modifier from the method signature of `renderWidget` inside `AbstractWidget`:

```java
// Chainlink1_21_1_Base.java - transformAbstractWidget
@Override
public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
    // a or b represents the obfuscated renderWidget method, descriptor (Lfhz;IIF)V is (GuiGraphics, int, int, float)V
    if (("a".equals(name) || "b".equals(name)) && "(Lfhz;IIF)V".equals(descriptor)) {
        // Strip the ACC_FINAL modifier
        access = access & ~Opcodes.ACC_FINAL;
    }
    return super.visitMethod(access, name, descriptor, signature, exceptions);
}
```

---

## 2. Widget Field Access Widening

Legacy mods often directly read or modify coordinates and states on widgets (e.g. repositioning button widgets on screen resizing). Modern coordinates are protected or require getter methods.

ChainLoader's `transformAbstractWidget` automatically widens these fields to `public`:

```java
// Chainlink1_21_1_Base.java - transformAbstractWidget
@Override
public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
    // c, d, g, h, e represent the obfuscated x, y, width, height, and active/visible fields
    if ("c".equals(name) || "d".equals(name) || "g".equals(name) || "h".equals(name) || "e".equals(name)) {
        access = (access & ~Opcodes.ACC_PRIVATE & ~Opcodes.ACC_PROTECTED) | Opcodes.ACC_PUBLIC;
    }
    return super.visitField(access, name, descriptor, signature, value);
}
```

---

## 3. Checkbox Constructor Backports

In Minecraft 1.21.1, the `Checkbox` widget constructor changed, removing the boolean state parameter and requiring developers to configure state after creation. 

Legacy mods calling the constructor expecting a boolean argument fail to load. ChainLoader backports this constructor by injecting the legacy signature and forwarding the values to the modern constructor:

```java
// Chainlink1_21_1_Base.java - transformCheckbox
@Override
public void visitEnd() {
    // Inject legacy constructor: <init>(int x, int y, int width, int height, Component title, boolean selected)
    MethodVisitor mv = super.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "(IIIILwz;Z)V", null, null);
    mv.visitCode();
    mv.visitVarInsn(Opcodes.ALOAD, 0);
    mv.visitVarInsn(Opcodes.ILOAD, 1);
    mv.visitVarInsn(Opcodes.ILOAD, 2);
    mv.visitVarInsn(Opcodes.ILOAD, 3);
    mv.visitVarInsn(Opcodes.ILOAD, 4);
    mv.visitVarInsn(Opcodes.ALOAD, 5); // Component title
    // Call modern super constructor Checkbox(x, y, width, height, title)
    mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "fid", "<init>", "(IIIILwz;)V", false);
    
    // Write the boolean parameter to the selected field
    mv.visitVarInsn(Opcodes.ALOAD, 0);
    mv.visitVarInsn(Opcodes.ILOAD, 6); // boolean selected
    mv.visitFieldInsn(Opcodes.PUTFIELD, "fio", "o", "Z"); // o is obfuscated selected state field
    
    mv.visitInsn(Opcodes.RETURN);
    mv.visitMaxs(6, 7);
    mv.visitEnd();
    super.visitEnd();
}
```

---

## 4. TransparentTextField & Widget Tick Remapping

In older versions of Minecraft, editable text inputs (e.g. `EditBox`/`TextFieldWidget` and custom derivatives like `TransparentTextField`) were updated by invoking a `tick()` method. In 1.21.1, the method was renamed to `tickWidget()`.

If a legacy screen class attempts to invoke `tick()` on a widget, it crashes with `NoSuchMethodError`. 

ChainLoader resolves this by scanning method calls. If a class calls `tick()` (or obfuscated equivalents like `method_2037`, `m_94120_`) on any object subclassing `AbstractWidget` (`isWidgetRecursive`), the call is redirected to `tickWidget()`:

```java
// Chainlink1_21_1_Base.java
boolean isTickMethod = "tick".equals(methodName) || "method_2037".equals(methodName) || "method_1684".equals(methodName) || "method_1868".equals(methodName) || "m_94120_".equals(methodName);

if (isTickMethod && "()V".equals(methodDesc) && isWidgetRecursive(owner)) {
    System.out.println("[Chainlink 1.21.1] Redirecting widget tick: owner=" + owner + ", name=" + methodName);
    methodName = "tickWidget";
}
```

### EventBridgeHelper stub
To prevent errors when legacy mods invoke this method but the class loader resolves it against a modern class that does not implement the tick method, `EventBridgeHelper` provides a stub:
```java
public static void tickWidget(Object widget) {
    // No-op to prevent NoSuchMethodError when legacy mods tick modern widgets.
}
```
This tick remapping ensures text fields and scrollable panels update their cursor flashes and scroll inputs correctly.

---

## 5. AWT / GLFW Resize Conflict Mitigation

When early loading screens are rendered using Java AWT/Swing in the same JVM process, AWT initializes its native graphics pipeline (DirectDraw, Direct3D, or OpenGL for Java2D hardware acceleration). 

This can clash with Minecraft's GLFW native OpenGL context when resizing the game window on Windows, causing Access Violations in graphics drivers (`atio6axx.dll`, `nvoglv64.dll`, or `opengl32.dll`) and hard JVM crashes.

To mitigate this, ChainLoader does two things:
1. Disables hardware acceleration for Java2D at startup inside `ChainLauncher.java`, forcing the Swing early loading screen window to render via CPU/software:
```java
System.setProperty("sun.java2d.noddraw", "true");
System.setProperty("sun.java2d.opengl", "false");
System.setProperty("sun.java2d.d3d", "false");
```
2. Disposes the Swing early loading screen window completely during control handover in `EarlyLoadingScreen.close()`:
```java
window.setVisible(false);
window.dispose();
window = null;
```
This terminates all native AWT peer contexts and threads, ensuring no AWT contexts are running concurrently with GLFW when the game window is resized.

---

## 6. Component.translatable Method Overloads Remapping

In modern Minecraft versions (1.20.5+), the static helper method `Component.translatable` has multiple overloads:
* `translatable(String)` -> maps to obfuscated method `c(String)`
* `translatable(String, Object...)` -> maps to obfuscated method `a(String, Object[])`

Legacy mods (or third-party libraries compiled against clean Mojang mappings) call `Component.translatable` under both signatures. If a compat layer unconditionally hardcodes a mapping for the method name `translatable` without verifying the descriptor/signature (e.g. unconditionally remapping `translatable` to `"c"`), the JVM will fail to resolve the varargs overload at runtime, throwing `java.lang.NoSuchMethodError: 'xn wz.c(java.lang.String, java.lang.Object[])'` (as occurred during JEI screen initialization).

ChainLoader's compatibility layers resolve this by checking the bytecode descriptor in the method remapping stage:
* If the descriptor starts with `(Ljava/lang/String;)`, it remaps to `c`.
* Otherwise, if it has arguments (varargs) e.g., `(Ljava/lang/String;[Ljava/lang/Object;)`, it maps to `a`.

---

## 7. Creative Tabs Pagination & Layout Remapping

Minecraft 1.21.1 expects a static set of 14 creative tabs arranged in a fixed layout of 6 columns across `TOP` and `BOTTOM` rows on a single page. It does not support multiple pages out of the box, meaning custom tabs registered by mods overflow off-screen or render invisibly.

To support paginated creative tabs for mods, ChainLoader implements a dynamic layout overrides and pagination system:
1. **Redirecting CreativeModeTabs.tabs()**: Overwrites the static `tabs()` method inside `CreativeModeTabs` (`ctb`) to call `EventBridgeHelper.getCreativeModeTabs()`. This returns only the tabs visible on the active page (Page 0 contains vanilla tabs; Page 1+ contains modded tabs, with 12 tabs per page).
2. **Tab Row/Column Overrides**: Intercepts `getRow()` (`g()`) and `getColumn()` (`f()`) inside `CreativeModeTab` (`cta`) to invoke `EventBridgeHelper.getCreativeTabRow(tab)` and `EventBridgeHelper.getCreativeTabColumn(tab)` dynamically. Modded tabs are placed on columns `0` to `5` on `TOP`/`BOTTOM` rows matching their relative index on the current page.
3. **Screen Rendering & Click Hooks**:
   - Injects rendering code at the end of `CreativeModeInventoryScreen.render()` to call `EventBridgeHelper.onCreativeScreenRender()` to draw previous, page number, and next page controls (`< Page X/Y >`) above the GUI.
   - Injects a mouse click interceptor at the start of `CreativeModeInventoryScreen.mouseClicked()` to process page navigation buttons, decrement/increment the page counter, and invoke `updateActiveTab()` to refresh the screen layout.


