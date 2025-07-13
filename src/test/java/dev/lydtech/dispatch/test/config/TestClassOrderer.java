package dev.lydtech.dispatch.test.config;

import dev.lydtech.dispatch.core.ActuatorInfoIT;
import org.junit.jupiter.api.ClassDescriptor;
import org.junit.jupiter.api.ClassOrderer;
import org.junit.jupiter.api.ClassOrdererContext;

import java.util.Comparator;

public class TestClassOrderer implements ClassOrderer {
    @Override
    public void orderClasses(ClassOrdererContext classOrdererContext) {
        classOrdererContext.getClassDescriptors().sort(Comparator.comparingInt(TestClassOrderer::getOrder));
    }

    private static int getOrder(ClassDescriptor classDescriptor) {
        String className = classDescriptor.getDisplayName();
        if (className.endsWith("Test") || className.endsWith("Tests")) {
            return 1;
        } else if (className.endsWith(ActuatorInfoIT.class.getSimpleName())) {
            return 2;
        } else if (className.endsWith("IT")) {
            return 3;
        } else {
            throw new IllegalArgumentException("Test class " + className + " does not end with 'Test', 'Tests','IT'");
        }
    }
}
