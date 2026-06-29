package net.minecraftforge.forgespi.language;

import java.util.Collection;
import java.util.Set;
import java.util.Map;
import org.objectweb.asm.Type;

public class ModFileScanData {
    public static record AnnotationData(
        Type annotationType,
        Type clazz,
        String memberName,
        Map<String, Object> annotationData
    ) {
        public Type getAnnotationType() { return annotationType; }
        public Type getClassType() { return clazz; }
        public String getMemberName() { return memberName; }
        public Map<String, Object> getAnnotationData() { return annotationData; }
    }

    public static record ClassData(
        Type clazz,
        Type parent,
        Set<Type> interfaces
    ) {
        public Type getClassType() { return clazz; }
        public Type getParentType() { return parent; }
        public Set<Type> getInterfaces() { return interfaces; }
    }

    private final Set<AnnotationData> annotations;
    private final Set<ClassData> classes;

    public ModFileScanData(Collection<AnnotationData> annotations, Set<ClassData> classes) {
        this.annotations = annotations instanceof Set ? (Set<AnnotationData>) annotations : new java.util.HashSet<>(annotations);
        this.classes = classes;
    }

    public Set<AnnotationData> getAnnotations() {
        return annotations;
    }

    public Set<ClassData> getClasses() {
        return classes;
    }
}
