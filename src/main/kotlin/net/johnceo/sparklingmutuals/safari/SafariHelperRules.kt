package net.johnceo.sparklingmutuals.safari

object SafariHelperRules {
    fun showNests(mode: SafariMode, run: SafariRun?, party: PartySparklingState) =
        (mode != SafariMode.SPARKLING || run?.partyNestsHandled != true) && needed("Honeybug", mode, run, party)
    val species = setOf("Honeybug", "Rockmite", "Snoozle")
    fun needed(species: String, mode: SafariMode, run: SafariRun?, party: PartySparklingState) = when (mode) {
        SafariMode.FULL_CLEAR -> true
        SafariMode.UNIQUE -> (run?.count(species) ?: 0) == 0 || party.needs(species) && run?.sparklingChecks?.checked(species) != true
        SafariMode.SPARKLING -> party.needs(species) && run?.sparklingChecks?.checked(species) != true
    }
}
