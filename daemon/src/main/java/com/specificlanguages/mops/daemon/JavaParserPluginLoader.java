package com.specificlanguages.mops.daemon;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.extensions.PluginId;

import java.lang.reflect.Method;

final class JavaParserPluginLoader {
    private JavaParserPluginLoader() {
    }

    static Method loadBridgeMethod() throws ClassNotFoundException, NoSuchMethodException {
        IdeaPluginDescriptor descriptor = PluginManager.getInstance().findEnabledPlugin(
            PluginId.getId(JavaSnippetParser.PLUGIN_ID)
        );
        if (descriptor == null) {
            throw new IllegalStateException(
                "MOPS daemon plugin " + JavaSnippetParser.PLUGIN_ID + " is not loaded or enabled"
            );
        }
        ClassLoader classLoader = descriptor.getPluginClassLoader();
        if (classLoader == null) {
            throw new IllegalStateException(
                "MOPS daemon plugin " + JavaSnippetParser.PLUGIN_ID + " has no plugin classloader"
            );
        }
        Class<?> bridgeClass = classLoader.loadClass(JavaSnippetParser.BRIDGE_CLASS);
        return bridgeClass.getMethod("invoke", String.class, Object.class, String.class, Object.class);
    }
}
