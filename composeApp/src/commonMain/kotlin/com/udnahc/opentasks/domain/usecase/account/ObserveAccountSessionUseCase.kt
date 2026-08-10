package com.udnahc.opentasks.domain.usecase.account

import com.udnahc.opentasks.data.auth.AccountRepository
import com.udnahc.opentasks.data.auth.AccountSessionState
import kotlinx.coroutines.flow.StateFlow

class ObserveAccountSessionUseCase(
    private val accountRepository: AccountRepository,
) {
    operator fun invoke(): StateFlow<AccountSessionState> = accountRepository.sessionState
}
