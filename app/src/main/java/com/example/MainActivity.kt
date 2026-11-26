package com.example

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.auth.AuthScreen
import com.example.auth.signOut
import com.example.data.MedicineRepository
import com.example.data.NetworkMonitor
import com.example.data.PreferencesRepository
import com.example.data.UpdateNotifier
import com.example.domain.UiState
import com.example.localization.AppLanguage
import com.example.localization.LocalAppStrings
import com.example.localization.stringsFor
import com.example.ui.SupplementViewModel
import com.example.ui.screens.AddEditMedicineScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.MedicineDetailScreen
import com.example.ui.screens.SearchScreen
import com.example.ui.screens.UpdatesScreen
import com.example.ui.theme.SupplementTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

private object Routes {
    const val HOME = "home"
    const val SEARCH = "search"
    const val UPDATES = "updates"
    const val ADD_EDIT = "add_edit?editId={editId}&prefill={prefill}"
    const val DETAIL = "detail/{medicineId}"

    fun addEdit(editId: String? = null, prefill: String? = null): String {
        val e = Uri.encode(editId.orEmpty())
        val p = Uri.encode(prefill.orEmpty())
        return "add_edit?editId=$e&prefill=$p"
    }

    fun detail(medicineId: String): String = "detail/${Uri.encode(medicineId)}"
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        UpdateNotifier.ensureChannel(this)

        setContent {
            SupplementTheme {
                AppContent()
            }
        }
    }
}

@Composable
private fun AppContent() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefsRepo = remember(context) { PreferencesRepository(context) }
    val language by prefsRepo.languageFlow.collectAsStateWithLifecycle(initialValue = AppLanguage.ENGLISH)
    val strings = remember(language) { stringsFor(language) }

    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }
    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose { Firebase.auth.removeAuthStateListener(listener) }
    }

    CompositionLocalProvider(LocalAppStrings provides strings) {
        val user = currentUser
        if (user == null) {
            AuthScreen(
                language = language,
                onToggleLanguage = { next ->
                    scope.launch { prefsRepo.setLanguage(next) }
                },
                onAuthSuccess = {
                    currentUser = Firebase.auth.currentUser
                }
            )
        } else {
            val uid = user.uid
            key(uid) {
                AuthenticatedSupplementApp(
                    currentUserId = uid,
                    preferencesRepository = prefsRepo
                )
            }
        }
    }
}

@Composable
private fun AuthenticatedSupplementApp(
    currentUserId: String,
    preferencesRepository: PreferencesRepository
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val viewModel: SupplementViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                SupplementViewModel(
                    repository = MedicineRepository(db),
                    preferencesRepository = preferencesRepository,
                    networkMonitor = NetworkMonitor(app),
                    currentUserId = currentUserId,
                    appContext = app.applicationContext
                )
            }
        }
    )

    val strings = LocalAppStrings.current
    val medicinesState by viewModel.medicinesState.collectAsStateWithLifecycle()
    val updatesState by viewModel.updatesState.collectAsStateWithLifecycle()
    val unreadUpdatesCount by viewModel.unreadUpdatesCount.collectAsStateWithLifecycle()
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
    val language by viewModel.language.collectAsStateWithLifecycle()
    val notificationsEnabled by viewModel.notificationsEnabled.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val suggestions by viewModel.searchSuggestions.collectAsStateWithLifecycle()
    val filteredMedicines by viewModel.filteredMedicines.collectAsStateWithLifecycle()

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val snackbarHostState = remember { SnackbarHostState() }

    // Surface an in-app snackbar alert when a new community update arrives in real time
    var seenLatestUpdateId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(updatesState) {
        val list = (updatesState as? UiState.Success)?.data ?: return@LaunchedEffect
        val latest = list.firstOrNull() ?: return@LaunchedEffect
        if (seenLatestUpdateId == null) {
            seenLatestUpdateId = latest.id
        } else if (latest.id != seenLatestUpdateId) {
            seenLatestUpdateId = latest.id
            if (latest.contributorUid != currentUserId) {
                snackbarHostState.showSnackbar(
                    "${strings.newCommunityUpdateToast} (${latest.brandName})"
                )
            }
        }
    }

    val isTopLevelTab = currentRoute in setOf(Routes.HOME, Routes.SEARCH, Routes.UPDATES)

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 700.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            floatingActionButton = {
                if (isTopLevelTab) {
                    if (useNavigationRail) {
                        ExtendedFloatingActionButton(
                            onClick = { navController.navigate(Routes.addEdit()) },
                            icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                            text = { Text(strings.addNewMedicine) },
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.testTag("fab_add_medicine")
                        )
                    } else {
                        FloatingActionButton(
                            onClick = { navController.navigate(Routes.addEdit()) },
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.testTag("fab_add_medicine")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = strings.addNewMedicine
                            )
                        }
                    }
                }
            },
            bottomBar = {
                if (!useNavigationRail && isTopLevelTab) {
                    NavigationBar(
                        windowInsets = WindowInsets.navigationBars,
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        NavigationBarItem(
                            selected = currentRoute == Routes.HOME,
                            onClick = {
                                navController.navigate(Routes.HOME) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    if (currentRoute == Routes.HOME) Icons.Rounded.Home else Icons.Outlined.Home,
                                    contentDescription = strings.navHome
                                )
                            },
                            label = { Text(strings.navHome) },
                            modifier = Modifier.testTag("nav_tab_home")
                        )
                        NavigationBarItem(
                            selected = currentRoute == Routes.SEARCH,
                            onClick = {
                                navController.navigate(Routes.SEARCH) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    if (currentRoute == Routes.SEARCH) Icons.Rounded.Search else Icons.Outlined.Search,
                                    contentDescription = strings.navSearch
                                )
                            },
                            label = { Text(strings.navSearch) },
                            modifier = Modifier.testTag("nav_tab_search")
                        )
                        NavigationBarItem(
                            selected = currentRoute == Routes.UPDATES,
                            onClick = {
                                navController.navigate(Routes.UPDATES) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                BadgedBox(
                                    badge = {
                                        if (unreadUpdatesCount > 0) {
                                            Badge {
                                                Text(unreadUpdatesCount.coerceAtMost(99).toString())
                                            }
                                        }
                                    }
                                ) {
                                    Icon(
                                        if (currentRoute == Routes.UPDATES) Icons.Rounded.Notifications else Icons.Outlined.Notifications,
                                        contentDescription = strings.navUpdates
                                    )
                                }
                            },
                            label = { Text(strings.navUpdates) },
                            modifier = Modifier.testTag("nav_tab_updates")
                        )
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavigationRail && isTopLevelTab) {
                    NavigationRail(
                        modifier = Modifier.fillMaxHeight(),
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        NavigationRailItem(
                            selected = currentRoute == Routes.HOME,
                            onClick = {
                                navController.navigate(Routes.HOME) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    if (currentRoute == Routes.HOME) Icons.Rounded.Home else Icons.Outlined.Home,
                                    contentDescription = strings.navHome
                                )
                            },
                            label = { Text(strings.navHome) }
                        )
                        NavigationRailItem(
                            selected = currentRoute == Routes.SEARCH,
                            onClick = {
                                navController.navigate(Routes.SEARCH) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    if (currentRoute == Routes.SEARCH) Icons.Rounded.Search else Icons.Outlined.Search,
                                    contentDescription = strings.navSearch
                                )
                            },
                            label = { Text(strings.navSearch) }
                        )
                        NavigationRailItem(
                            selected = currentRoute == Routes.UPDATES,
                            onClick = {
                                navController.navigate(Routes.UPDATES) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                BadgedBox(
                                    badge = {
                                        if (unreadUpdatesCount > 0) {
                                            Badge {
                                                Text(unreadUpdatesCount.coerceAtMost(99).toString())
                                            }
                                        }
                                    }
                                ) {
                                    Icon(
                                        if (currentRoute == Routes.UPDATES) Icons.Rounded.Notifications else Icons.Outlined.Notifications,
                                        contentDescription = strings.navUpdates
                                    )
                                }
                            },
                            label = { Text(strings.navUpdates) }
                        )
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = Routes.HOME,
                    modifier = Modifier.weight(1f)
                ) {
                    composable(Routes.HOME) {
                        HomeScreen(
                            medicinesState = medicinesState,
                            updatesState = updatesState,
                            unreadUpdatesCount = unreadUpdatesCount,
                            isOnline = isOnline,
                            language = language,
                            onToggleLanguage = viewModel::setLanguage,
                            onOpenSearch = {
                                navController.navigate(Routes.SEARCH) {
                                    launchSingleTop = true
                                }
                            },
                            onOpenAddMedicine = {
                                navController.navigate(Routes.addEdit())
                            },
                            onOpenMedicineDetail = { id ->
                                navController.navigate(Routes.detail(id))
                            },
                            onOpenUpdates = {
                                navController.navigate(Routes.UPDATES) {
                                    launchSingleTop = true
                                }
                            },
                            onSignOut = {
                                signOut(credentialManager = credentialManager, scope = scope)
                            }
                        )
                    }

                    composable(Routes.SEARCH) {
                        SearchScreen(
                            searchQuery = searchQuery,
                            selectedCategory = selectedCategory,
                            suggestions = suggestions,
                            filteredMedicines = filteredMedicines,
                            onSearchQueryChange = viewModel::onSearchQueryChanged,
                            onCategorySelected = viewModel::onCategorySelected,
                            onOpenMedicineDetail = { id ->
                                navController.navigate(Routes.detail(id))
                            },
                            onAddNewWithPrefill = { prefill ->
                                navController.navigate(Routes.addEdit(prefill = prefill))
                            }
                        )
                    }

                    composable(Routes.UPDATES) {
                        UpdatesScreen(
                            updatesState = updatesState,
                            notificationsEnabled = notificationsEnabled,
                            onSetNotificationsEnabled = viewModel::setNotificationsEnabled,
                            onMarkUpdatesSeen = viewModel::markUpdatesAsSeen,
                            onOpenMedicineDetail = { id ->
                                navController.navigate(Routes.detail(id))
                            }
                        )
                    }

                    composable(
                        route = Routes.ADD_EDIT,
                        arguments = listOf(
                            navArgument("editId") {
                                type = NavType.StringType
                                defaultValue = ""
                            },
                            navArgument("prefill") {
                                type = NavType.StringType
                                defaultValue = ""
                            }
                        )
                    ) { backStack ->
                        val editId = backStack.arguments?.getString("editId").orEmpty()
                        val prefill = backStack.arguments?.getString("prefill").orEmpty()
                        val existingToEdit = if (editId.isNotBlank()) {
                            viewModel.getMedicineById(editId)
                        } else null

                        AddEditMedicineScreen(
                            existingToEdit = existingToEdit,
                            prefillBrandName = prefill,
                            isOnline = isOnline,
                            findDuplicateCandidate = viewModel::findDuplicateCandidate,
                            onSubmitNew = { brand, generic, company, cat, boxPrice, pieces, fallback ->
                                viewModel.addMedicine(brand, generic, company, cat, boxPrice, pieces, fallback)
                            },
                            onSubmitCorrection = { medId, generic, company, cat, boxPrice, pieces, note, fallback ->
                                viewModel.submitCorrection(medId, generic, company, cat, boxPrice, pieces, note, fallback)
                            },
                            onNavigateBack = { navController.popBackStack() },
                            onOpenExistingMedicine = { medId ->
                                navController.navigate(Routes.detail(medId)) {
                                    popUpTo(Routes.HOME)
                                }
                            },
                            onSwitchToEditExisting = { medId ->
                                navController.navigate(Routes.addEdit(editId = medId)) {
                                    popUpTo(Routes.HOME)
                                }
                            },
                            onShowSuccess = { msg ->
                                scope.launch { snackbarHostState.showSnackbar(msg) }
                            }
                        )
                    }

                    composable(
                        route = Routes.DETAIL,
                        arguments = listOf(
                            navArgument("medicineId") { type = NavType.StringType }
                        )
                    ) { backStack ->
                        val medId = backStack.arguments?.getString("medicineId").orEmpty()
                        // Observe medicinesState/updatesState reactively so live updates show immediately on Details
                        val allMeds = (medicinesState as? UiState.Success)?.data ?: emptyList()
                        val allUpdates = (updatesState as? UiState.Success)?.data ?: emptyList()
                        val med = allMeds.firstOrNull { it.id == medId }
                        val history = allUpdates.filter { it.medicineId == medId }

                        MedicineDetailScreen(
                            medicine = med,
                            history = history,
                            onNavigateBack = { navController.popBackStack() },
                            onEditMedicine = { id ->
                                navController.navigate(Routes.addEdit(editId = id))
                            }
                        )
                    }
                }
            }
        }
    }
}
