package com.docscan.app.ui.paywall

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.docscan.app.R
import com.docscan.app.domain.model.Plan
import com.docscan.app.domain.model.PlanKind
import com.docscan.app.ui.components.appViewModel
import com.docscan.app.ui.theme.DocScanTheme
import com.docscan.app.ui.theme.Sizes
import com.docscan.app.ui.theme.Spacing

@Composable
fun PaywallRoute(onClose: () -> Unit) {
    val viewModel = appViewModel { PaywallViewModel(entitlements) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val pending = stringResource(R.string.paywall_pending)
    val failed = stringResource(R.string.paywall_failed)
    val restored = stringResource(R.string.paywall_restored)
    val nothing = stringResource(R.string.paywall_nothing_to_restore)

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                PaywallEvent.Purchased -> onClose()
                PaywallEvent.Pending -> snackbar.showSnackbar(pending)
                PaywallEvent.Failed -> snackbar.showSnackbar(failed)
                PaywallEvent.Restored -> snackbar.showSnackbar(restored)
                PaywallEvent.NothingToRestore -> snackbar.showSnackbar(nothing)
            }
        }
    }

    PaywallScreen(
        state = state,
        onSelect = viewModel::onSelect,
        onBuy = { viewModel.onBuy() },
        onRestore = { viewModel.onRestore() },
        onClose = onClose,
        snackbar = snackbar,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaywallScreen(
    state: PaywallUiState,
    onSelect: (String) -> Unit,
    onBuy: () -> Unit,
    onRestore: () -> Unit,
    onClose: () -> Unit,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    // Closing is always possible, from the first frame.
                    IconButton(onClick = onClose) {
                        Icon(
                            Icons.Outlined.Close,
                            stringResource(R.string.action_close)
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = Spacing.l),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            Text(
                stringResource(R.string.paywall_title),
                style = MaterialTheme.typography.headlineMedium
            )
            Text(
                stringResource(R.string.paywall_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Feature(Icons.Outlined.Draw, R.string.paywall_feature_signature)
            Feature(Icons.Outlined.WaterDrop, R.string.paywall_feature_watermark)
            Feature(Icons.Outlined.CheckCircle, R.string.paywall_feature_free_stays)

            when {
                state.isPro -> Text(
                    stringResource(R.string.paywall_already_pro),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )

                state.plans == null -> Box(
                    Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }

                state.plans.isEmpty() -> Text(
                    stringResource(R.string.paywall_unavailable),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )

                else -> Column(
                    Modifier.selectableGroup(),
                    verticalArrangement = Arrangement.spacedBy(Spacing.s)
                ) {
                    state.plans.forEach { plan ->
                        PlanCard(
                            plan,
                            selected = plan.id == state.selectedPlan?.id,
                            onClick = { onSelect(plan.id) })
                    }
                }
            }

            if (!state.isPro) {
                Button(
                    onClick = onBuy,
                    enabled = state.selectedPlan != null && !state.isBusy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.paywall_cta)) }
            }
            TextButton(
                onClick = onRestore,
                enabled = !state.isBusy,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text(stringResource(R.string.paywall_restore))
            }
            Text(
                stringResource(R.string.paywall_terms),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = Spacing.l),
            )
        }
    }
}

@Composable
private fun Feature(icon: ImageVector, text: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.m)
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Text(stringResource(text), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun PlanCard(plan: Plan, selected: Boolean, onClick: () -> Unit) {
    val (name, period) = when (plan.kind) {
        PlanKind.MONTHLY -> R.string.plan_monthly to R.string.plan_period_month
        PlanKind.YEARLY -> R.string.plan_yearly to R.string.plan_period_year
        PlanKind.LIFETIME -> R.string.plan_lifetime to R.string.plan_period_once
    }
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        border = BorderStroke(
            if (selected) Sizes.hairline * 2 else Sizes.hairline,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Row(Modifier.padding(Spacing.m), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = null)
            Column(Modifier
                .weight(1f)
                .padding(start = Spacing.m)) {
                Text(stringResource(name), style = MaterialTheme.typography.titleMedium)
                if (plan.kind == PlanKind.YEARLY) {
                    Text(
                        stringResource(R.string.plan_best_value),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Text(stringResource(period, plan.price), style = MaterialTheme.typography.titleMedium)
        }
    }
}

private val previewPlans = listOf(
    Plan("monthly", PlanKind.MONTHLY, "$2.99"),
    Plan("yearly", PlanKind.YEARLY, "$14.99"),
    Plan("lifetime", PlanKind.LIFETIME, "$29.99"),
)

@Preview(name = "Paywall – plans", showBackground = true)
@Composable
private fun PaywallPreview() =
    DocScanTheme { PaywallScreen(PaywallUiState(previewPlans), {}, {}, {}, {}) }

@Preview(name = "Paywall – loading", showBackground = true)
@Composable
private fun PaywallLoadingPreview() =
    DocScanTheme { PaywallScreen(PaywallUiState(), {}, {}, {}, {}) }

@Preview(name = "Paywall – store unavailable", showBackground = true)
@Composable
private fun PaywallUnavailablePreview() =
    DocScanTheme { PaywallScreen(PaywallUiState(emptyList()), {}, {}, {}, {}) }

@Preview(name = "Paywall – already Pro", showBackground = true)
@Composable
private fun PaywallProPreview() =
    DocScanTheme { PaywallScreen(PaywallUiState(previewPlans, isPro = true), {}, {}, {}, {}) }
