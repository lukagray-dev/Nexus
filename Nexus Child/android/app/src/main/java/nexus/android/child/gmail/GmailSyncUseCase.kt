package nexus.android.child.gmail

class GmailSyncUseCase(private val repository: GmailRepository) {

    suspend fun initialSync(limit: Int): List<GmailEmail> =
        repository.fetchInitial(limit)

    suspend fun incrementalSync(): List<GmailEmail> =
        repository.fetchIncremental()
}
