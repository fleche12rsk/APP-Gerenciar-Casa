package dev.micio.casa.core

/** Onde a configuração (com senhas e tokens) fica guardada em cada sistema. */
interface ConfigStorage {
    fun read(): String?
    fun write(text: String)
}

/** Ações que dependem do sistema: abrir outros apps. */
interface PlatformActions {
    /** "Android" ou "iOS", para textos de ajuda. */
    val name: String
    fun openAlexaApp()
    fun openIcseeApp()
}
