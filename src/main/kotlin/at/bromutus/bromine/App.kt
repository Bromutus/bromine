package at.bromutus.bromine

import at.bromutus.bromine.commands.Txt2Img
import at.bromutus.bromine.commands.Txt2Img.registerTxt2ImgCommand
import at.bromutus.bromine.sdclient.createSDClient
import dev.kord.core.Kord
import dev.kord.core.event.interaction.*
import dev.kord.core.on

suspend fun main() {
    val config = loadAppConfig()

    val sdClient = createSDClient(config.sdApiUrl)

    val kord = Kord(config.discordApiToken)

    kord.createGlobalApplicationCommands {
        registerTxt2ImgCommand()
    }
    kord.on<ChatInputCommandInteractionCreateEvent> {
        val commandName = interaction.invokedCommandName
        when (commandName) {
            Txt2Img.COMMAND_NAME -> Txt2Img.handle(interaction, sdClient)
        }
    }

    kord.login {
        println("Login successful")
    }
}

