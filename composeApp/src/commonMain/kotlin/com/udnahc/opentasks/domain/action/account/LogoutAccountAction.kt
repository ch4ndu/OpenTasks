package com.udnahc.opentasks.domain.action.account

import com.udnahc.opentasks.data.auth.AccountRepository
import com.udnahc.opentasks.data.auth.AccountSessionState

class LogoutAccountAction(
    private val accountRepository: AccountRepository,
) {
    suspend operator fun invoke(): AccountSessionState = accountRepository.logout()
}
