package blue.eaudouce.waybiker

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest

object SupabaseInstance {
    val client = createSupabaseClient(
        supabaseUrl = "https://qjqjpjiqrsorvcvpsyfx.supabase.co",
        supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InFqcWpwamlxcnNvcnZjdnBzeWZ4Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3NTIxMTI0OTcsImV4cCI6MjA2NzY4ODQ5N30.tUCnvdT1hnw-55_baNiDLOOW8tfGMo7wQ-2oxQeZeGw"
    ) {
        install(Postgrest)
        install(Auth)
    }
}