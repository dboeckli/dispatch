package dev.lydtech.dispatch.test.config;

import dev.lydtech.dispatch.health.ActuatorInfoIT;
import org.junit.jupiter.api.ClassDescriptor;
import org.junit.jupiter.api.ClassOrderer;
import org.junit.jupiter.api.ClassOrdererContext;

import java.util.Comparator;

public class TestClassOrderer implements ClassOrderer {
    private static int getOrder(ClassDescriptor classDescriptor) {
        String actuatorPackage = ActuatorInfoIT.class.getPackage().getName();
        if (classDescriptor.getTestClass().getPackage().getName().equals(actuatorPackage)) {
            return 2;
        }
        String className = classDescriptor.getTestClass().getSimpleName();
        return switch (className) {
            case String name when name.endsWith("IT") -> 3;
            case String name when name.endsWith("Test") || name.endsWith("Tests") -> 1;
            default -> Integer.MAX_VALUE;
        };
    }

    @Override
    public void orderClasses(ClassOrdererContext classOrdererContext) {
        classOrdererContext.getClassDescriptors().sort(Comparator.comparingInt(TestClassOrderer::getOrder));
    }
}
