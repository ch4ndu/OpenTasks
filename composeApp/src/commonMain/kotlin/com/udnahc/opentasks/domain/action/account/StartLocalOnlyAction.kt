package com.udnahc.opentasks.domain.action.account

import com.udnahc.opentasks.data.auth.AccountRepository

class StartLocalOnlyAction(
    private val accountRepository: AccountRepository,
) {
    suspend operator fun invoke() = accountRepository.startLocalOnly()
}
