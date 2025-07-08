package chenjunfu2.areareplace;

import net.fabricmc.api.ModInitializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AreaReplace implements ModInitializer {
	public static final String MOD_ID = "areareplace";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.

		LOGGER.info("如果你在服务器后台上见到这句话，请卸载这个Mod，因为这个Mod并非用于服务端，它没有任何服务端功能");
		LOGGER.info("If you see this message on the server backend, please uninstall this Mod, as it is not intended for the server side and has no server-side functionality.");
	}
}