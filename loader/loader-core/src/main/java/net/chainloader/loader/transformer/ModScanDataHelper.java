package net.chainloader.loader.transformer;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import net.chainloader.loader.core.ModScanner;

import java.io.InputStream;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ModScanDataHelper {
    private static List<net.neoforged.neoforgespi.language.ModFileScanData> neoForgeScanData;
    private static List<net.minecraftforge.forgespi.language.ModFileScanData> forgeScanData;

    public static synchronized List<net.neoforged.neoforgespi.language.ModFileScanData> getNeoForgeScanData() {
        if (neoForgeScanData == null) {
            initScanData();
        }
        return neoForgeScanData;
    }

    public static synchronized List<net.minecraftforge.forgespi.language.ModFileScanData> getForgeScanData() {
        if (forgeScanData == null) {
            initScanData();
        }
        return forgeScanData;
    }

    private static void initScanData() {
        neoForgeScanData = new ArrayList<>();
        forgeScanData = new ArrayList<>();

        for (ModScanner.DiscoveredMod mod : ModScanner.getDiscoveredMods()) {
            if (mod.jarFile == null) {
                continue;
            }

            List<net.neoforged.neoforgespi.language.ModFileScanData.AnnotationData> neoAnnots = new ArrayList<>();
            List<net.minecraftforge.forgespi.language.ModFileScanData.AnnotationData> forgeAnnots = new ArrayList<>();
            Set<net.neoforged.neoforgespi.language.ModFileScanData.ClassData> neoClasses = new HashSet<>();
            Set<net.minecraftforge.forgespi.language.ModFileScanData.ClassData> forgeClasses = new HashSet<>();

            try (ZipFile zipFile = new ZipFile(mod.jarFile)) {
                Enumeration<? extends ZipEntry> entries = zipFile.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    if (entry.getName().endsWith(".class")) {
                        try (InputStream is = zipFile.getInputStream(entry)) {
                            ClassReader cr = new ClassReader(is);
                            cr.accept(new ClassVisitor(Opcodes.ASM9) {
                                @Override
                                public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                                    if (descriptor.equals("Lmezz/jei/api/JeiPlugin;")) {
                                        Type annotType = Type.getType(descriptor);
                                        Type classType = Type.getType("L" + cr.getClassName() + ";");
                                        
                                        neoAnnots.add(new net.neoforged.neoforgespi.language.ModFileScanData.AnnotationData(
                                            annotType, classType, cr.getClassName().replace('/', '.'), new HashMap<>()
                                        ));
                                        
                                        forgeAnnots.add(new net.minecraftforge.forgespi.language.ModFileScanData.AnnotationData(
                                            annotType, classType, cr.getClassName().replace('/', '.'), new HashMap<>()
                                        ));
                                    }
                                    return null;
                                }
                            }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                        } catch (Exception e) {
                            // Ignore corrupted classes
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("[ChainLoader] Failed to scan annotations for mod: " + mod.metadata.getId() + " - " + e.getMessage());
            }

            for (net.neoforged.neoforgespi.language.ModFileScanData.AnnotationData ad : neoAnnots) {
                neoClasses.add(new net.neoforged.neoforgespi.language.ModFileScanData.ClassData(
                    ad.clazz(), Type.getType("Ljava/lang/Object;"), new HashSet<>()
                ));
            }
            for (net.minecraftforge.forgespi.language.ModFileScanData.AnnotationData ad : forgeAnnots) {
                forgeClasses.add(new net.minecraftforge.forgespi.language.ModFileScanData.ClassData(
                    ad.clazz(), Type.getType("Ljava/lang/Object;"), new HashSet<>()
                ));
            }

            if (!neoAnnots.isEmpty() || !forgeAnnots.isEmpty()) {
                neoForgeScanData.add(new net.neoforged.neoforgespi.language.ModFileScanData(neoAnnots, neoClasses));
                forgeScanData.add(new net.minecraftforge.forgespi.language.ModFileScanData(forgeAnnots, forgeClasses));
            }
        }
    }
}
