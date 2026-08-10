package com.udnahc.opentasks.domain.action.account

import com.udnahc.opentasks.data.auth.AccountRepository
import com.udnahc.opentasks.data.auth.AccountSessionState

class ReauthenticateAccountAction(
    private val accountRepository: AccountRepository,
) {
    suspend operator fun invoke(email: String, password: String): AccountSessionState =
        accountRepository.reauthenticate(email, password)
}
