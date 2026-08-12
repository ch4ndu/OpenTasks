package com.udnahc.opentasks.domain.action.settings

import com.udnahc.opentasks.data.auth.AccountRepository

class ClearLocalDataAction(
    private val accountRepository: AccountRepository,
) {
    suspend operator fun invoke() = accountRepository.clearLocalData()
}
