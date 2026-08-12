package com.udnahc.opentasks.domain.action.account

import com.udnahc.opentasks.data.auth.AccountRepository

class PrepareLocalServerReplacementAction(
    private val accountRepository: AccountRepository,
) {
    suspend operator fun invoke(endpoint: String, email: String, password: String) =
        accountRepository.prepareLocalServerReplacement(endpoint, email, password)
}
