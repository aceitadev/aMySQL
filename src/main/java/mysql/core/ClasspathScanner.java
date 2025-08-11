package mysql.core;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

public class ClasspathScanner {

    public static List<Class<?>> findClasses(String basePackage, Class<? extends Annotation> annotation) throws IOException, ClassNotFoundException {
        List<Class<?>> classes = new ArrayList<>();
        String path = basePackage.replace('.', '/');
        Enumeration<URL> resources = Thread.currentThread().getContextClassLoader().getResources(path);

        while (resources.hasMoreElements()) {
            URL resource = resources.nextElement();
            File directory = new File(resource.getFile());
            if (directory.exists()) {
                for (File file : findFiles(directory, ".class")) {
                    String className = basePackage + '.' + file.getName().substring(0, file.getName().length() - 6);
                    Class<?> cls = Class.forName(className);
                    if (cls.isAnnotationPresent(annotation)) {
                        classes.add(cls);
                    }
                }
            }
        }
        return classes;
    }

    private static List<File> findFiles(File directory, String suffix) {
        List<File> files = new ArrayList<>();
        if (directory.isDirectory()) {
            File[] listedFiles = directory.listFiles();
            if (listedFiles != null) {
                for (File file : listedFiles) {
                    files.addAll(findFiles(file, suffix));
                }
            }
        } else if (directory.getName().endsWith(suffix)) {
            files.add(directory);
        }
        return files;
    }
}