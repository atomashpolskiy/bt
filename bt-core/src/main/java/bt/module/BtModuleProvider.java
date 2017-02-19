package bt.module;

import com.google.inject.Module;

/**
 * Provides the support for auto-loading of modules.
 * Module providers are specified in META-INF/services/bt.module.BtModuleProvider file.
 *
 * @see java.util.ServiceLoader
 * @since 1.1
 */
public interface BtModuleProvider {

    /**
     * @since 1.1
     */
    Module module();
}
