package com.udnahc.opentasks.domain.action.account

import com.udnahc.opentasks.data.auth.AccountRepository
import com.udnahc.opentasks.data.auth.AccountSessionState

class SwitchAccountAction(
    private val accountRepository: AccountRepository,
) {
    suspend operator fun invoke(
        endpoint: String,
        email: String,
        password: String,
    ): AccountSessionState = accountRepository.switchAccount(endpoint, email, password)
}
