package com.movedados.witon.data.remote

import com.movedados.witon.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage

/**
 * Cliente unico do Supabase.
 *
 * A anon key e publica por design: quem protege os dados e o RLS, nao o segredo da chave.
 * A service_role key NUNCA pode entrar no app.
 */
object SupabaseModule {

    val client: SupabaseClient by lazy {
        require(BuildConfig.SUPABASE_URL.isNotBlank()) {
            "SUPABASE_URL vazio. Copie local.properties.example para local.properties."
        }
        require(BuildConfig.SUPABASE_ANON_KEY.isNotBlank()) {
            "SUPABASE_ANON_KEY vazio. Copie local.properties.example para local.properties."
        }
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_ANON_KEY
        ) {
            install(Auth) {
                autoLoadFromStorage = true
                alwaysAutoRefresh = true
            }
            install(Postgrest)
            install(Storage)
        }
    }
}
