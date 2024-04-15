package com.assessment.studentselection

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.SharedPreferences
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.assessment.R
import com.assessment.databinding.StudentSelectionBinding
import com.assessment.flow.AssessmentConstants.KEY_GRADE
import com.assessment.flow.AssessmentConstants.KEY_SCHOOL_DATA
import com.assessment.flow.AssessmentConstants.KEY_STUDENT_ID
import com.assessment.flow.assessment.AssessmentFlowActivity
import com.assessment.flow.workflowengine.AppConstants
import com.assessment.flow.workflowengine.UtilityFunctions
import com.data.db.models.Summary
import com.data.db.models.entity.School
import com.data.db.models.helper.StudentWithAssessmentHistory
import com.data.helper.ObjectConvertor.toSchoolData
import com.data.models.submissions.StudentNipunStates
import com.example.assets.uielements.CustomMessageDialog
import com.google.android.flexbox.FlexDirection
import com.google.android.flexbox.FlexboxLayoutManager
import com.google.android.flexbox.JustifyContent
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationResult
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.samagra.ancillaryscreens.data.prefs.CommonsPrefsHelperImpl
import com.samagra.commons.GeofencingHelper
import com.samagra.commons.basemvvm.BaseActivity
import com.samagra.commons.posthog.APP_ID
import com.samagra.commons.posthog.EID_INTERACT
import com.samagra.commons.posthog.EVENT_LOCATION_MATCHED
import com.samagra.commons.posthog.EVENT_LOCATION_NOT_MATCHED
import com.samagra.commons.posthog.EVENT_STUDENT_SCREEN_ANONYMOUS_ASSESSMENT_STARTED
import com.samagra.commons.posthog.EVENT_STUDENT_SCREEN_ASSESSMENT_STARTED
import com.samagra.commons.posthog.EVENT_STUDENT_SCREEN_BACK_CLICKED
import com.samagra.commons.posthog.EVENT_STUDENT_SCREEN_GRADE_SELECTED
import com.samagra.commons.posthog.EVENT_STUDENT_SCREEN_MONTH_CHANGED
import com.samagra.commons.posthog.EVENT_TYPE_USER_ACTION
import com.samagra.commons.posthog.NL_APP_STUDENT_SELECTION
import com.samagra.commons.posthog.PostHogManager
import com.samagra.commons.posthog.STUDENT_SELECTION_SCREEN
import com.samagra.commons.posthog.data.Cdata
import com.samagra.commons.utils.NetworkStateManager
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date

const val REQUEST_CHECK_SETTINGS: Int = 122

private const val SUMMARY_LIST = "summary_list"

private const val GRADE_ONE = "1"
private const val GRADE_TWO = "2"
private const val GRADE_THREE = "3"
private const val GRADE_FOUR = "4"
private const val GRADE_FIVE = "5"

@AndroidEntryPoint
class StudentSelectionActivity : BaseActivity<StudentSelectionBinding, StudentSelectionViewModel>(),
    StudentAdapter.OnItemClickListener {
    private lateinit var studentAdapter: StudentAdapter
    private lateinit var nipunStatesAdapter: NipunStatesAdapter
    private var selectedTextView: TextView? = null
    private var gradesList: List<Int> = mutableListOf()
    private val gradeViews: MutableList<TextView> = mutableListOf()
    private var selectedClassIndex = 0
    private var school: School? = null
    private var selectedMonth: Int = 0
    private var selectedYear: Int = 0
    private var currentMonthOfYear: Int = 0
    private lateinit var prefs: CommonsPrefsHelperImpl
    private var geofencingRadius: Int? = 0
    private val geofencingConfig by lazy {
        GeofencingHelper.parseGeofencingConfig()
    }
    private var startLocationMatchDialog: CustomMessageDialog? = null
    private var userLatitude: Double = 0.0
    private var userLongitude: Double = 0.0
    private var accuracy: Float = 0.0f
    private var isClassesSet = false
    private var areDummyStudentsAdded = false

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        when {
            permissions.getOrElse(Manifest.permission.ACCESS_FINE_LOCATION) { false } -> {
                changeLocationSetting()
            }

            permissions.getOrElse(Manifest.permission.ACCESS_COARSE_LOCATION) { false } -> {
                changeLocationSetting()
            }

            else -> {
                //handle denied permissions
                finish()
            }
        }
    }

    override fun layoutRes(): Int {
        return R.layout.student_selection
    }

    override fun getBaseViewModel(): StudentSelectionViewModel {
        val viewModel: StudentSelectionViewModel by viewModels()
        return viewModel
    }

    override fun getBindingVariable(): Int {
        return 0;
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (checkGeofencingEnabled()
            && school?.geofencingEnabled == true
        ) {
            launchGeofencingFlow()
        } else {
            //do something when required
        }
    }

    //FIX for https://console.firebase.google.com/project/mission-prerna/crashlytics/app/android:org.samagra.missionPrerna/issues/8bb6124f12df07fc858fc7a21884471b
    override fun onStop() {
        super.onStop()
        stopLocationUpdates()
    }

    private fun launchGeofencingFlow() {
        GeofencingHelper.checkShowRequestPermissionRationale(
            activity = this,
            launchGeofence = {
                locationPermissionRequest.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            },
            launchSettings = {
                GeofenceUI.allowLocationPermissionDialog(this, {
                    finish()
                }, {
                    startActivity(Intent().apply {
                        action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        data = Uri.fromParts("package", packageName, null)
                    })
                    finish()
                })
            }
        )
    }

    private fun checkGeofencingEnabled(): Boolean {
        val enabled: Boolean = if (geofencingConfig?.enabled == true) {
            geofencingConfig?.actorsDisabled?.contains(prefs.mentorDetailsData.actorId)?.not()
                ?: false
        } else {
            false
        }
        if (enabled) {
            this.geofencingRadius = geofencingConfig?.geofencingInitials?.fencingRadius
        }
        Timber.d("enabled : $enabled")
        return enabled
    }

    private fun changeLocationSetting() {
        GeofencingHelper.changeLocationSettings(
            activity = this,
            success = {
                getCurrentLocation()
                setStartMatchingLocationDialog()
            },
            failure = { exception ->
                if (exception is ResolvableApiException) {
                    try {
                        startIntentSenderForResult(
                            exception.resolution.intentSender,
                            REQUEST_CHECK_SETTINGS,
                            null,
                            0,
                            0,
                            0,
                            null
                        )
                    } catch (sendEx: IntentSender.SendIntentException) {
                        // Ignore the error.
                    }
                }
            }
        )
    }

    private fun getCurrentLocation() {
        startLocationUpdates()
    }

    private fun startLocationUpdates() {
        fusedLocationClient.requestLocationUpdates(
            GeofencingHelper.getLocationRequest(),
            locationCallback,
            Looper.getMainLooper()
        )
    }

    private fun setStartMatchingLocationDialog() {
        startLocationMatchDialog = GeofenceUI.startLocationMatchDialog(this)
        startLocationMatchDialog?.setOnFinishListener {
            startLocationMatchDialog?.dismiss()
            finish()
        }
    }

    private val fusedLocationClient: FusedLocationProviderClient by lazy {
        GeofencingHelper.getFusedLocationProviderClient(this)
    }

    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
        Timber.d("location updates removed!")
    }

    private fun hideLocationDialog() {
        if (startLocationMatchDialog?.isShowing == true)
            startLocationMatchDialog?.dismiss()
    }

    private fun showOutOfRangePrompt() {
        GeofenceUI.locationMatchFailureDialog(this, geofencingConfig?.dialogProps) {
            finish()
        }
    }

    private fun performAction() {
        GeofenceUI.locationMatchedDialog(this) {
            it.dismiss()
        }
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            val lastLocation = locationResult.lastLocation
            userLatitude = lastLocation?.latitude ?: 0.0
            userLongitude = lastLocation?.longitude ?: 0.0
            accuracy = lastLocation?.accuracy ?: 0.0f

            GeofencingHelper.compareUserLocationWithGivenRange(
                userLatitude = userLatitude,
                userLongitude = userLongitude,
                schoolData = school?.toSchoolData()!!,
                geofencingRadius = geofencingRadius,
                listener = object : GeofencingHelper.SetMatchLocationListener {
                    override fun onLocationRangeMatched(distance: Float) {
                        stopLocationUpdates()
                        hideLocationDialog()
                        performAction()
                        sendLocationEvent(distance, true)
                    }

                    override fun onLocationOutOfRange(distance: Float) {
                        stopLocationUpdates()
                        hideLocationDialog()
                        showOutOfRangePrompt()
                        sendLocationEvent(distance, false)
                    }

                    override fun onLocationLatLongNull() {
                        stopLocationUpdates()
                        hideLocationDialog()
                        Toast.makeText(baseContext, "School lat long is null!", Toast.LENGTH_LONG).show()
                    }
                }
            )
        }
    }

    private fun sendLocationEvent(distance: Float, matched: Boolean) {
        val list = ArrayList<Cdata>()
        val mentorDetailsFromPrefs = prefs.mentorDetailsData
        mentorDetailsFromPrefs?.let {
            list.add(Cdata("userId", "" + it.id))
            list.add(Cdata("userType", "" + it.actorId))
        }

        list.add(Cdata("udise", "" + school?.udise))
        list.add(Cdata("userLatitude", "" + userLatitude))
        list.add(Cdata("userLongitude", "" + userLongitude))
        list.add(Cdata("userAccuracy", "" + accuracy))
        list.add(Cdata("schoolLatitude", "" + school?.schoolLat))
        list.add(Cdata("schoolLongitude", "" + school?.schoolLong))
        list.add(Cdata("shortestDistance", "" + distance))

        val properties = PostHogManager.createProperties(
            STUDENT_SELECTION_SCREEN,
            EVENT_TYPE_USER_ACTION,
            EID_INTERACT,
            PostHogManager.createContext(APP_ID, NL_APP_STUDENT_SELECTION, list),
            null,
            null,
            PreferenceManager.getDefaultSharedPreferences(this)
        )
        if (matched){
            PostHogManager.capture(this, EVENT_LOCATION_MATCHED, properties)
        } else {
            PostHogManager.capture(this, EVENT_LOCATION_NOT_MATCHED, properties)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CHECK_SETTINGS) {
            if (resultCode == Activity.RESULT_OK) {
                launchGeofencingFlow()
            } else if (resultCode == Activity.RESULT_CANCELED) {
                GeofenceUI.enableLocationToStartFlow(this, {
                    finish()
                }, {
                    launchGeofencingFlow()
                })
            } else {
                //handle edge case if required
                Toast.makeText(this, getString(R.string.enable_location), Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onLoadData() {
        initPreferences()
        getDataFromIntent()
        setupToolbar()
        supportActionBar!!.setDisplayShowTitleEnabled(false);
        setCurrentMonthAndText()
        setClickListeners()
        setObserver()
        setupRecyclerView()
        viewModel.getGradesList()
        viewModel.fetchStudents(school?.udise!!)
    }

    private fun initPreferences() {
        prefs = CommonsPrefsHelperImpl(this, "prefs")
    }

    private fun getDataFromIntent() {
        if (intent.hasExtra(AppConstants.INTENT_SCHOOL_DATA)) {
            school =
                intent.getSerializableExtra(AppConstants.INTENT_SCHOOL_DATA) as School
        } else {
            Timber.i("School data not found")
            finish()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_student_selection_toolbar, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.refresh -> {
                if (NetworkStateManager.instance?.networkConnectivityStatus == false) {
                    showToast(getString(R.string.error_network_issue))
                    return true
                }
                showProgressBar()
                viewModel.fetchStudents(school?.udise!!)
            }
        }
        return true
    }

    private fun sendGradeSelectionEvent(grade: String) {
        val list = ArrayList<Cdata>()
        val mentorDetailsFromPrefs = prefs.mentorDetailsData
        mentorDetailsFromPrefs?.let {
            list.add(Cdata("userId", "" + it.id))
        }
        list.add(Cdata("grade", "" + grade))
        list.add(Cdata("latitude", "" + userLatitude))
        list.add(Cdata("longitude", "" + userLongitude))

        val properties = PostHogManager.createProperties(
            STUDENT_SELECTION_SCREEN,
            EVENT_TYPE_USER_ACTION,
            EID_INTERACT,
            PostHogManager.createContext(APP_ID, NL_APP_STUDENT_SELECTION, list),
            null,
            null,
            PreferenceManager.getDefaultSharedPreferences(this)
        )
        PostHogManager.capture(this, EVENT_STUDENT_SCREEN_GRADE_SELECTED, properties)
    }

    private fun sendMonthSwitchedEvent(month: Int) {
        val list = ArrayList<Cdata>()
        val mentorDetailsFromPrefs = prefs.mentorDetailsData
        mentorDetailsFromPrefs?.let {
            list.add(Cdata("userId", "" + it.id))
        }
        list.add(Cdata("month", "" + month))
        list.add(Cdata("latitude", "" + userLatitude))
        list.add(Cdata("longitude", "" + userLongitude))

        val properties = PostHogManager.createProperties(
            STUDENT_SELECTION_SCREEN,
            EVENT_TYPE_USER_ACTION,
            EID_INTERACT,
            PostHogManager.createContext(APP_ID, NL_APP_STUDENT_SELECTION, list),
            null,
            null,
            PreferenceManager.getDefaultSharedPreferences(this)
        )
        PostHogManager.capture(this, EVENT_STUDENT_SCREEN_MONTH_CHANGED, properties)
    }

    private fun sendStudentAssessmentStartedEvent(studentId: String) {
        val list = ArrayList<Cdata>()
        val mentorDetailsFromPrefs = prefs.mentorDetailsData
        mentorDetailsFromPrefs?.let {
            list.add(Cdata("userId", "" + it.id))
        }
        list.add(Cdata("studentId", "" + studentId))
        list.add(Cdata("latitude", "" + userLatitude))
        list.add(Cdata("longitude", "" + userLongitude))

        val properties = PostHogManager.createProperties(
            STUDENT_SELECTION_SCREEN,
            EVENT_TYPE_USER_ACTION,
            EID_INTERACT,
            PostHogManager.createContext(APP_ID, NL_APP_STUDENT_SELECTION, list),
            null,
            null,
            PreferenceManager.getDefaultSharedPreferences(this)
        )
        PostHogManager.capture(this, EVENT_STUDENT_SCREEN_ASSESSMENT_STARTED, properties)
    }

    private fun sendAnonymousStudentAssessmentStartedEvent(studentId: String) {
        val list = ArrayList<Cdata>()
        val mentorDetailsFromPrefs = prefs.mentorDetailsData
        mentorDetailsFromPrefs?.let {
            list.add(Cdata("userId", "" + it.id))
        }
        list.add(Cdata("studentId", "" + studentId))
        list.add(Cdata("latitude", "" + userLatitude))
        list.add(Cdata("longitude", "" + userLongitude))

        val properties = PostHogManager.createProperties(
            STUDENT_SELECTION_SCREEN,
            EVENT_TYPE_USER_ACTION,
            EID_INTERACT,
            PostHogManager.createContext(APP_ID, NL_APP_STUDENT_SELECTION, list),
            null,
            null,
            PreferenceManager.getDefaultSharedPreferences(this)
        )
        PostHogManager.capture(this, EVENT_STUDENT_SCREEN_ANONYMOUS_ASSESSMENT_STARTED, properties)
    }

    private fun sendBackPressedEvent() {
        val list = ArrayList<Cdata>()
        val mentorDetailsFromPrefs = prefs.mentorDetailsData
        mentorDetailsFromPrefs?.let {
            list.add(Cdata("userId", "" + it.id))
            list.add(Cdata("latitude", "" + userLatitude))
            list.add(Cdata("longitude", "" + userLongitude))
        }
        val properties = PostHogManager.createProperties(
            STUDENT_SELECTION_SCREEN,
            EVENT_TYPE_USER_ACTION,
            EID_INTERACT,
            PostHogManager.createContext(APP_ID, NL_APP_STUDENT_SELECTION, list),
            null,
            null,
            PreferenceManager.getDefaultSharedPreferences(this)
        )
        PostHogManager.capture(this, EVENT_STUDENT_SCREEN_BACK_CLICKED, properties)
    }

    override fun onBackPressed() {
        sendBackPressedEvent()
        super.onBackPressed()
    }

    private fun setClickListeners() {
        binding.toolbar.toolbar.setNavigationOnClickListener { finish() }
        updateMonthsArrowState()
        binding.ivPreviousMonth.setOnClickListener {
            if (selectedMonth - 1 > 0) {
                --selectedMonth
                sendMonthSwitchedEvent(selectedMonth)
                changeCurrentMonthText(selectedMonth)
                viewModel.fetchStudentsAssessmentHistoryInfo(
                    school?.udise!!,
                    selectedClassIndex + 1,
                    selectedMonth,
                    selectedYear
                )
                updateMonthsArrowState()
            }
        }
        binding.ivNextMonth.setOnClickListener {
            if (selectedMonth + 1 < 13) {
                ++selectedMonth
                sendMonthSwitchedEvent(selectedMonth)
                changeCurrentMonthText(selectedMonth)
                viewModel.fetchStudentsAssessmentHistoryInfo(
                    school?.udise!!,
                    selectedClassIndex + 1,
                    selectedMonth,
                    selectedYear
                )
                updateMonthsArrowState()
            }
        }
    }

    private fun updateMonthsArrowState() {
        when (selectedMonth) {
            currentMonthOfYear -> {
                binding.ivNextMonth.visibility = View.GONE
                binding.ivPreviousMonth.visibility = View.VISIBLE
            }

            1 -> {
                binding.ivNextMonth.visibility = View.VISIBLE
                binding.ivPreviousMonth.visibility = View.GONE
            }

            else -> {
                binding.ivNextMonth.visibility = View.VISIBLE
                binding.ivPreviousMonth.visibility = View.VISIBLE
            }
        }
    }

    private fun setupRecyclerView() {
        val studentSharedViewModel = ViewModelProvider(this)[StudentSharedViewModel::class.java]
        studentAdapter = StudentAdapter(
            mutableListOf(),
            studentSharedViewModel = studentSharedViewModel
        )
        studentAdapter.setOnItemClickListener(this)

        binding.rvStudentsList.apply {
            adapter = studentAdapter
            layoutManager = LinearLayoutManager(this@StudentSelectionActivity)
        }
        nipunStatesAdapter = NipunStatesAdapter()

        val flexboxLayoutManager = FlexboxLayoutManager(this)
        flexboxLayoutManager.flexDirection = FlexDirection.ROW
        flexboxLayoutManager.justifyContent = JustifyContent.FLEX_START

        binding.rvNipunStates.apply {
            adapter = nipunStatesAdapter
            layoutManager = flexboxLayoutManager
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar.toolbar)
        binding.toolbar.title.text = getString(R.string.assessment)
        binding.toolbar.tvVersion.text = UtilityFunctions.getVersionName(this)
    }

    @SuppressLint("SimpleDateFormat")
    private fun changeCurrentMonthText(month: Int) {
        when (month) {
            1 -> setCurrentMonthText(getString(R.string.month_january))
            2 -> setCurrentMonthText(getString(R.string.month_february))
            3 -> setCurrentMonthText(getString(com.assessment.R.string.month_march))
            4 -> setCurrentMonthText(getString(R.string.month_april))
            5 -> setCurrentMonthText(getString(R.string.month_may))
            6 -> setCurrentMonthText(getString(R.string.month_june))
            7 -> setCurrentMonthText(getString(R.string.month_july))
            8 -> setCurrentMonthText(getString(R.string.month_august))
            9 -> setCurrentMonthText(getString(R.string.month_september))
            10 -> setCurrentMonthText(getString(R.string.month_october))
            11 -> setCurrentMonthText(getString(R.string.month_november))
            12 -> setCurrentMonthText(getString(R.string.month_december))
        }
    }

    private fun setCurrentMonthText(month: String) {
        binding.tvMonth.text = month
    }

    private fun setClasses() {
        createAndDistributeTextViews(binding.clClassesButtons, gradesList)
    }

    private fun createAndDistributeTextViews(
        constraintLayout: ConstraintLayout,
        gradesList: List<Int>
    ) {
        // Clear existing TextViews
        constraintLayout.removeAllViews()
        gradeViews.clear()
        val constraintSet = ConstraintSet()
        constraintSet.clone(constraintLayout)

        val chainIds = mutableListOf<Int>()

        for (i in 1..gradesList.size) {
            //all distinct grades in student listing api - get from db
            val classTextView = createTextView("${getString(R.string.class_word)} ${gradesList[i - 1]}")
            classTextView.background.setTint(ContextCompat.getColor(this, R.color.white))
            classTextView.setTextColor(ContextCompat.getColor(this, R.color.blue_31328f))

            val dpWidth = 102
            val dpHeight = 42
            // Convert dp to pixels
            val pixelsWidth = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dpWidth.toFloat(), resources.displayMetrics
            ).toInt()
            val pixelsHeight = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dpHeight.toFloat(), resources.displayMetrics
            ).toInt()

            // Set the constraints for the TextViews
            constraintSet.constrainWidth(classTextView.id, pixelsWidth)
            constraintSet.constrainHeight(classTextView.id, pixelsHeight)

            constraintLayout.addView(classTextView)

            // Add the TextView's ID to the chain
            chainIds.add(classTextView.id)
            gradeViews.add(classTextView)

            if (i > 1) {
                // Create a horizontal chain
                constraintSet.createHorizontalChain(
                    ConstraintSet.PARENT_ID,
                    ConstraintSet.LEFT,
                    ConstraintSet.PARENT_ID,
                    ConstraintSet.RIGHT,
                    chainIds.toIntArray(),
                    null,
                    ConstraintSet.CHAIN_SPREAD
                )
            }

            // If there's only one TextView, center it horizontally
            if (gradesList.size == 1) {
                constraintSet.centerHorizontally(chainIds[0], ConstraintSet.PARENT_ID)
            }

            classTextView.setOnClickListener {
                handleTextViewClick(classTextView)
            }
        }
        // Apply the constraints
        constraintSet.applyTo(constraintLayout)
        if (gradeViews.size > 0) {
            handleTextViewClick(gradeViews.get(selectedClassIndex))
        } else {
            selectedClassIndex = 0;
        }

    }

    private fun handleTextViewClick(textView: TextView) {
        // Reset last text view clicked state
        selectedTextView?.let {
            it.background.setTint(ContextCompat.getColor(this, R.color.white))
            it.setTextColor(ContextCompat.getColor(this, R.color.blue_31328f))
        }

        textView.background.setTint(ContextCompat.getColor(this, R.color.blue_31328f))
        textView.setTextColor(ContextCompat.getColor(this, R.color.white))

        // Store the currently selected TextView
        selectedTextView = textView

        val classText = textView.text.toString()
        if (classText.contains(GRADE_ONE)) {
            sendGradeSelectionEvent(GRADE_ONE)
            selectedClassIndex = 0;
            viewModel.fetchStudentsAssessmentHistoryInfo(
                school?.udise!!,
                1,
                selectedMonth,
                selectedYear
            )
        } else if (classText.contains(GRADE_TWO)) {
            sendGradeSelectionEvent(GRADE_TWO)
            selectedClassIndex = 1;
            viewModel.fetchStudentsAssessmentHistoryInfo(
                school?.udise!!,
                2,
                selectedMonth,
                selectedYear
            )
        } else if (classText.contains(GRADE_THREE)) {
            sendGradeSelectionEvent(GRADE_THREE)
            selectedClassIndex = 2;
            viewModel.fetchStudentsAssessmentHistoryInfo(
                school?.udise!!,
                3,
                selectedMonth,
                selectedYear
            )
        } else if (classText.contains(GRADE_FOUR)) {
            sendGradeSelectionEvent(GRADE_FOUR)
            selectedClassIndex = 3;
            viewModel.fetchStudentsAssessmentHistoryInfo(
                school?.udise!!,
                4,
                selectedMonth,
                selectedYear
            )
        } else if (classText.contains(GRADE_FIVE)) {
            sendGradeSelectionEvent(GRADE_FIVE)
            selectedClassIndex = 4;
            viewModel.fetchStudentsAssessmentHistoryInfo(
                school?.udise!!,
                5,
                selectedMonth,
                selectedYear
            )
        }
    }

    private fun setCurrentMonthAndText() {
        val dateFormat: DateFormat = SimpleDateFormat("M")
        val date = Date()
        selectedMonth = dateFormat.format(date).toInt()
        changeCurrentMonthText(selectedMonth)
        val calendar = Calendar.getInstance()
        selectedYear = calendar.get(Calendar.YEAR)
        currentMonthOfYear = dateFormat.format(date).toInt()
    }

    private fun createTextView(text: String): TextView {
        val textView = TextView(this)
        textView.id = View.generateViewId()
        textView.text = text
        textView.gravity = Gravity.CENTER
        textView.background =
            ContextCompat.getDrawable(this, R.drawable.ic_rect_border_select_class)
        textView.setTextColor(ContextCompat.getColor(this, R.color.blue_2e3192))
        textView.textSize = 16F
        textView.setTypeface(null, Typeface.BOLD)
        return textView
    }

    private fun setObserver() {

        lifecycleScope.launchWhenStarted {
            viewModel.studentAssessmentHistoryState.collect {
                when (it) {
                    is StudentAssessmentHistoryStates.Loading -> {
                        showProgressBar()
                        binding.progressBar.visibility = View.VISIBLE
                        binding.tvError.visibility = View.GONE
                    }

                    is StudentAssessmentHistoryStates.Error -> {
                        hideProgressBar()
                        binding.progressBar.visibility = View.GONE
                        binding.tvError.visibility = View.VISIBLE
                    }

                    is StudentAssessmentHistoryStates.Success -> {
                        hideProgressBar()
                        binding.progressBar.visibility = View.GONE
                        binding.tvError.visibility = View.GONE
                        val list = it.studentsAssessmentHistory
                        if (!areDummyStudentsAdded && list.size == 0) {
                            viewModel.addDummyStudents(school?.udise!!)
                            areDummyStudentsAdded = true
                        }
                        if (selectedMonth == (Calendar.getInstance().get(Calendar.MONTH) + 1)) {
                            val anonymousId = (selectedClassIndex + 1) * -1
                            val anonymousStudent =
                                StudentWithAssessmentHistory(
                                    id = anonymousId.toString(),
                                    name = "",
                                    rollNo = anonymousId.toLong(),
                                    month = 0,
                                    grade = selectedClassIndex + 1,
                                    status = null,
                                    last_assessment_date = null,
                                    isPlaceHolderStudent = true
                                )
                            list.add(anonymousStudent)
                        }
                        studentAdapter.addAll(list)
                        nipunStatesAdapter.submitList(getNipunStatesSummary(list))
                        Timber.tag("summary-from-submit").d(getNipunStatesSummary(list).toString())
                    }
                }
            }
        }

        lifecycleScope.launchWhenStarted {
            viewModel.studentAssessmentHistoryCompleteInfoState.collect {
                when (it) {
                    is StudentAssessmentHistoryCompleteInfoStates.Loading -> {
                        showProgressBar()
                        binding.progressBar.visibility = View.VISIBLE
                        binding.tvError.visibility = View.GONE
                    }

                    is StudentAssessmentHistoryCompleteInfoStates.Error -> {
                        hideProgressBar()
                        binding.progressBar.visibility = View.GONE
                        binding.tvError.visibility = View.VISIBLE
                    }

                    is StudentAssessmentHistoryCompleteInfoStates.Success -> {
                        hideProgressBar()
                        binding.progressBar.visibility = View.GONE
                        binding.tvError.visibility = View.GONE
                        saveLabelAndColour(it.studentsAssessmentHistoryCompleteInfo?.summary)
                    }
                }
            }
        }


        /*        lifecycleScope.launchWhenStarted {
                    viewModel.uiState.collect {
                        when (it) {
                            is StudentScreenStates.Loading -> {
                                binding.progressBar.visibility = View.VISIBLE
                                binding.tvError.visibility = View.GONE
                            }

                            is StudentScreenStates.Error -> {
                                binding.progressBar.visibility = View.GONE
                                binding.tvError.visibility = View.VISIBLE
                            }

                            is StudentScreenStates.Success -> {
                                binding.progressBar.visibility = View.GONE
                                binding.tvError.visibility = View.GONE
                                studentAdapter.differ.submitList(it.studentList)
                            }
                        }
                    }
                }*/

        lifecycleScope.launchWhenStarted {
            viewModel.gradesListState.collect {
                when (it) {
                    is GradesStates.Loading -> {
                        binding.progressBar.visibility = View.VISIBLE
                        binding.tvError.visibility = View.GONE
                    }

                    is GradesStates.Error -> {
                        hideProgressBar()
                        binding.progressBar.visibility = View.GONE
                        if (it.t.message.equals("yet to sync submission")) {
                            showToast("Cannot sync at the moment")
                        } else {
                            binding.tvError.visibility = View.VISIBLE
                        }
                    }

                    is GradesStates.Success -> {
                        hideProgressBar()
                        binding.progressBar.visibility = View.GONE
                        binding.tvError.visibility = View.GONE
                        if (!isClassesSet) {
                            gradesList = if (gradesList.isNotEmpty())
                                it.gradesList
                            else
                                getDefaultGradesList()
                            setClasses()
                            isClassesSet = true
                        }
                    }
                }
            }
        }
    }

    private fun getDefaultGradesList(): List<Int>{
        return listOf(1,2,3)
    }

    private fun saveLabelAndColour(list: List<Summary>?) {
        val sharedPreferencesManager = SharedPreferencesManager(this)
        sharedPreferencesManager.saveSummaryList(SUMMARY_LIST, list)
    }

    class SharedPreferencesManager(context: Context) {
        private val sharedPreferences: SharedPreferences =
            context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
        private val gson = Gson()

        fun saveSummaryList(key: String, summaryList: List<Summary>?) {
            val json = gson.toJson(summaryList)
            sharedPreferences.edit().putString(key, json).apply()
        }

        fun getSummaryList(key: String): List<Summary> {
            val json = sharedPreferences.getString(key, null)
            val type = object : TypeToken<List<Summary>>() {}.type
            return gson.fromJson(json, type) ?: emptyList()
        }
    }

    private fun getNipunStatesSummary(list: List<StudentWithAssessmentHistory>): List<Summary> {
        var nipunCount = 0
        var nipunLabel = ""
        var nipunColour = ""
        var notNipunCount = 0
        var notNipunLabel = ""
        var notNipunColour = ""
        var notAssessedCount = 0
        var notAssessedLabel = ""
        var notAssessedColour = ""

        val sharedPreferencesManager = SharedPreferencesManager(this)
        val retrievedSummaryList = sharedPreferencesManager.getSummaryList(SUMMARY_LIST)

        if (retrievedSummaryList.isNotEmpty()) {
            retrievedSummaryList.forEach {
                when (it.identifier) {
                    StudentNipunStates.pass -> {
                        nipunLabel = it.label
                        nipunColour = it.colour
                    }

                    StudentNipunStates.fail -> {
                        notNipunLabel = it.label
                        notNipunColour = it.colour
                    }

                    StudentNipunStates.pending -> {
                        notAssessedLabel = it.label
                        notAssessedColour = it.colour
                    }
                }
            }
        } else {
            nipunLabel = "Nipun"
            nipunColour = "#72BA86"
            notNipunLabel = "Not Nipun"
            notNipunColour = "#C98A7A"
            notAssessedLabel = "Not Assessed"
            notAssessedColour = "#E2E2E2"
        }

        list.forEach { studentItem ->
            when (studentItem.status) {
                StudentNipunStates.pending -> {
                    ++notAssessedCount
                }

                StudentNipunStates.pass -> {
                    ++nipunCount
                }

                StudentNipunStates.fail -> {
                    ++notNipunCount
                }
            }
        }

        var summary = mutableListOf<Summary>()

        summary.add(Summary(nipunColour, nipunCount, nipunLabel, StudentNipunStates.pass))
        summary.add(Summary(notNipunColour, notNipunCount, notNipunLabel, StudentNipunStates.fail))
        summary.add(
            Summary(
                notAssessedColour,
                notAssessedCount,
                notAssessedLabel,
                StudentNipunStates.pending
            )
        )

        return summary
    }

    override fun onItemClick(student: StudentWithAssessmentHistory) {
        if (student.isPlaceHolderStudent) {
            sendAnonymousStudentAssessmentStartedEvent(student.id)
        } else {
            sendStudentAssessmentStartedEvent(student.id)
        }
        val intent = Intent(
            this,
            AssessmentFlowActivity::class.java
        )
        intent.putExtra(KEY_STUDENT_ID, student.id)
        intent.putExtra(KEY_GRADE, student.grade)
        intent.putExtra(KEY_SCHOOL_DATA, school)
        startActivity(intent)
    }
}