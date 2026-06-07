package com.github.ffalcinelli.jdivert.windivert;

import com.github.ffalcinelli.jdivert.ebpfdivert.EBPFDivertJnaNativeAdapter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class NativeAdapterFactoryLinuxTestCase {

    @Test
    @EnabledOnOs(OS.LINUX)
    public void testLinuxAdapter() {
        NativeAdapter adapter = NativeAdapterFactory.getAdapter();
        assertNotNull(adapter);
        
        String javaVersion = System.getProperty("java.version");
        int major = getJavaMajorVersion(javaVersion);
        
        if (major >= 22) {
            String className = adapter.getClass().getName();
            assertTrue(className.equals("com.github.ffalcinelli.jdivert.ebpfdivert.EBPFDivertPanamaNativeAdapter") || 
                       adapter instanceof EBPFDivertJnaNativeAdapter,
                    "On Linux with Java 22+, it should be Panama or JNA adapter. Found: " + className);
        } else {
            assertTrue(adapter instanceof EBPFDivertJnaNativeAdapter,
                    "On Linux with Java < 22, it should be JNA adapter. Found: " + adapter.getClass().getName());
        }
    }

    private int getJavaMajorVersion(String version) {
        String[] parts = version.split("\\.");
        if (parts[0].equals("1")) {
            return Integer.parseInt(parts[1]);
        } else {
            String major = parts[0];
            int dashIndex = major.indexOf('-');
            if (dashIndex != -1) {
                major = major.substring(0, dashIndex);
            }
            return Integer.parseInt(major);
        }
    }
}
