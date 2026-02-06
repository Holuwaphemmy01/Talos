package com.talos.guardian.ui.auth

import com.talos.guardian.data.IAuthRepository
import com.talos.guardian.data.TalosUser
import com.talos.guardian.data.UserRole
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    private lateinit var viewModel: AuthViewModel
    private lateinit var repository: IAuthRepository
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        repository = mockk()
        viewModel = AuthViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `register success with parent role emits NavigateToDashboard`() = runTest(testDispatcher) {
        // Given
        val email = "test@example.com"
        val password = "password123"
        viewModel.email = email
        viewModel.password = password

        coEvery { repository.register(email, password, "PARENT") } returns Result.success(true)
        coEvery { repository.getCurrentUserProfile() } returns TalosUser(uid="123", email=email, role=UserRole.PARENT)

        // When
        viewModel.register()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        val effect = viewModel.authEffect.first()
        assertEquals(AuthEffect.NavigateToDashboard, effect)
    }

    @Test
    fun `register failure emits ShowToast with error message`() = runTest(testDispatcher) {
        // Given
        val email = "test@example.com"
        val password = "password123"
        viewModel.email = email
        viewModel.password = password
        val errorMsg = "Email already in use"

        coEvery { repository.register(email, password, "PARENT") } returns Result.failure(Exception(errorMsg))

        // When
        viewModel.register()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        val effect = viewModel.authEffect.first()
        assertEquals(AuthEffect.ShowToast(errorMsg), effect)
    }

    @Test
    fun `register with empty email shows toast`() = runTest(testDispatcher) {
        // Given
        viewModel.email = ""
        viewModel.password = "pass"

        // When
        viewModel.register()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        val effect = viewModel.authEffect.first()
        assertEquals(AuthEffect.ShowToast("Password must be 6+ chars"), effect)
    }
}
