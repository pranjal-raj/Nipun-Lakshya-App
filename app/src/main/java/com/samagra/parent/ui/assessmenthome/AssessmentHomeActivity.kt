package com.samagra.parent.ui.assessmenthome

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.annotation.LayoutRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.os.LocaleListCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.samagra.ancillaryscreens.data.prefs.CommonsPrefsHelperImpl
import com.samagra.commons.CompositeDisposableHelper
import com.samagra.commons.basemvvm.BaseActivity
import com.samagra.commons.constants.UserConstants
import com.samagra.commons.helper.GatekeeperHelper
import com.samagra.commons.models.schoolsresponsedata.SchoolsData
import com.samagra.commons.utils.addFragment
import com.samagra.parent.AppConstants
import com.samagra.parent.BR
import com.samagra.parent.R
import com.samagra.parent.UtilityFunctions
import com.samagra.parent.databinding.ActivityAssessmentHomeBinding
import com.samagra.parent.ui.DrawerOptions
import com.samagra.parent.ui.faq.HelpFaqActivity
import com.samagra.parent.ui.logout.LogoutUI
import com.samagra.parent.ui.privacypolicy.PrivacyPolicyActivity
import com.samagra.parent.ui.setBody
import dagger.hilt.android.AndroidEntryPoint
import io.samagra.odk.collect.extension.utilities.ODKProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import timber.log.Timber

@AndroidEntryPoint
class AssessmentHomeActivity : BaseActivity<ActivityAssessmentHomeBinding, AssessmentHomeVM>() {

    private var schoolsData: SchoolsData? = null
    private var dialogShowing: Boolean = false
    private var dialogBuilder: AlertDialog? = null
    private val prefs: CommonsPrefsHelperImpl by lazy { initPreferences() }
    private val STORAGE_PERMSSION_REQUEST_CODE = 111

    @LayoutRes
    override fun layoutRes() = R.layout.activity_assessment_home

    override fun getBaseViewModel(): AssessmentHomeVM {
        val hiltViewModel: AssessmentHomeVM by viewModels()
        return hiltViewModel
    }

    override fun loadFragment() {
        val fragment = if (prefs.selectedUser.equals(AppConstants.USER_TEACHER, true)) {
            AssessmentTeacherHomeFragment.newInstance(schoolsData)
        } else if(prefs.selectedUser.equals(AppConstants.USER_EXAMINER, true)) {
            AssessmentExaminerHomeFragment.newInstance()
        } else {
            AssessmentUserHomeFragment.newInstance()
        }
        addFragment(
            binding.container.id,
            supportFragmentManager,
            fragment,
            fragment.tag.toString(),
            false
        )

    }

    override fun getBindingVariable() = BR.assessmentHomeVm

    override fun onLoadData() {
        setupToolbar()
        getDataFromIntent()
//        setObservers()
//        setupOverViewUI()
//        callApis(true)
//        setListeners()
        GatekeeperHelper.assess(
            context = this,
            actor = prefs.selectedUser
        )
    }

//    private fun setBlockVisibility(visibility: Int) {
//        binding.groupBlock.visibility = visibility
//    }

    private fun getDataFromIntent() {
        if (intent.hasExtra(AppConstants.INTENT_SCHOOL_DATA)) {
            schoolsData =
                intent.getSerializableExtra(AppConstants.INTENT_SCHOOL_DATA) as SchoolsData
        }
        if (schoolsData == null && prefs.selectedUser.equals(AppConstants.USER_TEACHER, true)) {
            with(prefs.mentorDetailsData) {
                schoolsData = SchoolsData(
                    this?.udise,
                    this?.schoolName,
                    this?.schoolId,
                    true,
                    this?.schoolDistrict,
                    this?.schoolDistrictId,
                    this?.schoolBlock,
                    this?.schoolBlockId,
                    this?.schoolNyayPanchayat,
                    this?.schoolNyayPanchayatId,
                    this?.schoolLat,
                    this.schoolLong,
                    this?.schoolGeoFenceEnabled
                )
            }
        }
        Timber.d(
            "Schools data for udise assessmentTypeSelection : ${schoolsData?.udise.toString()}"
        )
    }

    private fun callApis(enforce: Boolean) {
        viewModel.downloadDataFromRemoteConfig(prefs, UtilityFunctions.isInternetAvailable(this))
        viewModel.syncDataFromServer(prefs, enforce)
        viewModel.checkForFallback(prefs)
    }
/*

    private fun setListeners() {
        binding.swipeRefresh.setOnRefreshListener {
            callApis(enforce = true)
        }
    }
*/

    /*override fun onResume() {
        super.onResume()
        if (dialogShowing) {
            showSyncAlertDialog()
        }
        setSyncButtonUI()
    }*/

    private fun showSyncAlertDialog() {
        dialogBuilder?.let {
            if (it.isShowing) {
                return@let
            } else {
                it.show()
            }
        } ?: run {
            dialogBuilder =
                AlertDialog.Builder(this).setMessage(getString(R.string.data_sync_successful))
                    .setPositiveButton(getText(R.string.ok)) { dialog, _ ->
                        dialog.dismiss()
                    }.show()
        }
    }

/*
    private fun setSyncButtonUI() {
        binding.mtlBtnSetupAssessment.visibility = View.VISIBLE
        binding.mtlBtnSetupAssessment.minLines = 2
    }
*/

    /*private fun setObservers() {
        with(viewModel) {
            observe(setupNewAssessmentClicked, ::handleSetupNewAssessment)
            observe(mentorDetailsSuccess, ::handleMentorDetails)
            observe(updateSync, ::handleSyncFlow)
            observe(mentorOverViewData, ::handleOverviewData)
            observe(failure, ::handleFailure)
            observe(showToastResWithId, ::handleMessage)
            observe(showSyncBeforeLogout, ::handleSyncBeforeLogout)
            observe(logoutUserLiveData, ::handleLogoutUser)
            observe(gotoLogin, ::handleLogoutRedirection)
            observe(progressBarVisibility, ::handleProgressBarVisibility)
        }
    }*/

    private fun handleLogoutRedirection(@Suppress("UNUSED_PARAMETER") unit: Unit?) {
//        setRedirectionsOnIntent()
    }

    /*  private fun handleLogoutUser(@Suppress("UNUSED_PARAMETER") unit: Unit?) {
          LogoutUI.confirmLogout(this) {
              viewModel.onLogoutUserData(prefs)
          }
      }
  */
    /*  private fun handleSyncBeforeLogout(@Suppress("UNUSED_PARAMETER") unit: Unit?) {
          confirmLogoutWithSync()
      }
  */
    private fun confirmLogoutWithSync() {
        LogoutUI.confirmLogoutWithSync(this) {
            viewModel.syncDataToServer(prefs, {
                viewModel.onLogoutUserData(prefs)
            }) {
                Toast.makeText(this, R.string.error_generic_message, Toast.LENGTH_LONG).show()
            }
        }
    }

    /*   private fun handleSyncFlow(msg: Int?) {
           msg?.let {
               ToastUtils.showShortToast(it)
           }
           setSyncButtonUI()
       }
   */
    /*  override fun onPause() {
          super.onPause()
          dialogBuilder?.let {
              if (it.isShowing) {
                  dialogShowing = true
                  it.dismiss()
              }
          }
      }
  */
    /*   private fun handleOverviewData(overview: HomeOverviewData?) {
           setupOverViewUIWithData(overview)
       }

       private fun handleMessage(textResId: Int?) {
           textResId?.let {
               ToastUtils.showShortToast(it)
           }
       }
   */
    private fun initPreferences() = CommonsPrefsHelperImpl(this, "prefs")


    /*   private fun setupOverViewUI() {
           binding.tvMonth.text =
               String.format(getString(R.string.oveview_month_basis), getString(UtilityFunctions.getCurrentMonth()))
   //            "आपके अवलोकन ${getString(UtilityFunctions.getCurrentMonth())} महीने पर आधारित हैं"
           if (prefs.selectedUser.equals(AppConstants.USER_TEACHER, true)) {
               binding.clProfileOverview.visibility = View.GONE
               binding.clOverview.visibility = View.VISIBLE
               binding.includeAssessmentOverview.titleAssessmentsField.text =
                   getString(R.string.spot_assessment_overview)
               binding.includeAssessmentOverview.cvBox1.visibility = View.GONE
           } else if (prefs.selectedUser.equals(AppConstants.USER_EXAMINER, true)) {
               binding.titleMentorDetails.text = getString(R.string.examiner_profile)
               binding.clProfileOverview.visibility = View.VISIBLE
               binding.clOverview.visibility = View.VISIBLE
           } else {
               if (prefs.selectedUser.equals(Constants.USER_DIET_MENTOR, true)) {
                   binding.titleMentorDetails.text = getString(R.string.diet_mentor_profile)
               }
               binding.clProfileOverview.visibility = View.VISIBLE
               binding.clOverview.visibility = View.VISIBLE
           }

           with(binding.includeAssessmentOverview) {
               tvCount1.text = Constants.ZERO
               tvCount2.text = Constants.ZERO
               tvCount3.text = String.format("${Constants.ZERO} %s", getString(R.string.min_one))
               tvNameBox1.text = getString(R.string.schools_visited)
               tvNameBox2.text = getString(R.string.students_assessed)
               tvNameBox3.text = getString(R.string.average_time_per_assessment)
               titleAssessmentsField.text = getString(R.string.visits_overview)
           }
           with(binding.includeGradeWiseOverview) {
               tvCount1.text = Constants.ZERO
               tvCount2.text = Constants.ZERO
               tvCount3.text = Constants.ZERO
               tvNameBox1.text = getString(R.string.grade_1_students)
               tvNameBox2.text = getString(R.string.grade_2_students)
               tvNameBox3.text = getString(R.string.grade_3_students)
               titleAssessmentsField.text = getString(R.string.grade_wise_overview)
           }
       }
   */
    /*   private fun setupOverViewUIWithData(overview: HomeOverviewData?) {
           with(binding.includeAssessmentOverview) {
               if (prefs.selectedUser.equals(AppConstants.USER_TEACHER, true)) {
                   this.titleAssessmentsField.text = getString(R.string.spot_assessment_overview)
                   this.cvBox1.visibility = View.GONE
               } else if (prefs.selectedUser.equals(AppConstants.USER_EXAMINER, true)) {
                   this.cvBox1.visibility = View.VISIBLE
                   this.titleAssessmentsField.text = getString(R.string.visits_overview)
               } else {
                   this.cvBox1.visibility = View.VISIBLE
                   this.titleAssessmentsField.text = getString(R.string.visits_overview)
               }
               tvCount1.text = overview?.schoolsVisited.toString()
               tvCount2.text = overview?.studentsAssessed.toString()
               if (CommonUtilities.convertSecondToMinute(overview?.avgTimePerStudent ?: 0) > 1) {
                   tvCount3.text =
                       String.format(
                           getString(R.string._min),
                           CommonUtilities.convertSecondToMinute(overview?.avgTimePerStudent ?: 0)
                               .toString()
                       )
               } else {
                   //if no students assessed show 0 min else 1
                   val minute = if ((overview?.studentsAssessed ?: 0) < 1) 0 else 1
                   tvCount3.text = String.format(getString(R.string._min_one), minute)
               }
           }
           with(binding.includeGradeWiseOverview) {
               tvCount1.text = overview?.grade1Students.toString()
               tvCount2.text = overview?.grade2Students.toString()
               tvCount3.text = overview?.grade3Students.toString()
           }
       }
   */
    /*   private fun handleSetupNewAssessment(@Suppress("UNUSED_PARAMETER") unit: Unit?) {
           setPostHogEventSetupAssessment()
           startActivity(Intent(this, AssessmentSetupActivity::class.java))
       }
   */
    /*private fun setPostHogEventSetupAssessment() {
        val properties = PostHogManager.createProperties(
            DASHBOARD_SCREEN,
            EVENT_TYPE_USER_ACTION,
            EID_INTERACT,
            PostHogManager.createContext(APP_ID, NL_APP_DASHBOARD, ArrayList()),
            Edata(NL_DASHBOARD, TYPE_CLICK),
            Object.Builder().id(SETUP_ASSESSMENT_BUTTON).type(OBJ_TYPE_UI_ELEMENT).build()
        )
        PostHogManager.capture(this, EVENT_SETUP_ASSESSMENT, properties)
    }*/

    /*private fun handleFailure(errorMessage: String?) {
        ToastUtils.showShortToast(errorMessage)
    }*/

    /*private fun handleMentorDetails(result: Result?) {
        val designation =
            MetaDataExtensions.getDesignationFromId(
                result?.designation_id ?: 0,
                prefs.designationsListJson
            )
        result?.let {
            Timber.e("user id mentors ${it.id}")
            if (designation.equals(Constants.USER_DESIGNATION_SRG, true)) {
                setBlockVisibility(View.GONE)
            } else {
                setBlockVisibility(View.VISIBLE)
            }
        }
    }*/

    private fun setupToolbar() {
        with(binding) {
            includeToolbar.toolbar.navigationIcon =
                AppCompatResources.getDrawable(
                    this@AssessmentHomeActivity,
                    R.drawable.ic_baseline_menu_24
                )
            supportActionBar?.setDisplayShowTitleEnabled(true)
            includeToolbar.tvVersion.text =
                UtilityFunctions.getVersionName(this@AssessmentHomeActivity)
            includeToolbar.toolbar.title = getString(R.string.nipun_lakshya_app)
            includeToolbar.toolbar.setNavigationOnClickListener {
                /*if (prefs.selectedUser.equals(AppConstants.USER_TEACHER, true)) {
                    dl.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
                    onBackHandling()
                } else {
                    dl.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
                    if (dl.isDrawerVisible(GravityCompat.START)) {
                        dl.closeDrawer(GravityCompat.START)
                    } else {
                        dl.openDrawer(GravityCompat.START)
                    }
                }
*/
                dl.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
                if (dl.isDrawerVisible(GravityCompat.START)) {
                    dl.closeDrawer(GravityCompat.START)
                } else {
                    dl.openDrawer(GravityCompat.START)
                }
            }
            /*if (prefs.selectedUser.equals(AppConstants.USER_TEACHER, true)) {
                includeToolbar.toolbar.setNavigationIcon(R.drawable.ic_backspace_24)
            } else {
            }*/
            includeToolbar.toolbar.setNavigationIcon(R.drawable.ic_baseline_menu_24)
            dl.setBody(this@AssessmentHomeActivity, prefs) {
                onDrawerClickHandler(it)
            }
        }
    }

    private fun onBackHandling() {
        finish()
    }

    private fun onDrawerClickHandler(drawerItem: DrawerItem) {
        when (drawerItem.drawerOptionType) {
            DrawerOptions.KNOWLEDGE -> knowledge()
            DrawerOptions.HELP -> help()
            DrawerOptions.LOGOUT -> {
                binding.dl.closeDrawer(GravityCompat.START)
                viewModel.onLogoutClicked()
            }
            DrawerOptions.ZIP -> {
                onClickDownloadZip()
            }
            DrawerOptions.PRIVACY_POLICY -> {
                binding.dl.closeDrawer(GravityCompat.START)
                openPrivacyPolicy()
            }
            DrawerOptions.CHANGE_LANGUAGE -> {
                binding.dl.closeDrawer(GravityCompat.START)
                changeLanguage()
            }
            DrawerOptions.CHANGE_LANGUAGE2->
            {
                binding.dl.closeDrawer(GravityCompat.START)
                changeLanguage2()
            }
        }
    }

    private fun changeLanguage() {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en"))
        prefs.locale = "en"
    }

    private fun changeLanguage2() {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("hi"))
        prefs.locale = "hi"
    }

    private fun onClickDownloadZip(){
        val formsInDb= ODKProvider.getFormsDatabaseInteractor().getLocalForms().size
        val formsFromWorkflow = JSONArray(prefs.getString(UserConstants.FORMS_LIST_PREFS_KEY, null)).length()

        if (formsInDb >= formsFromWorkflow) {
            if ((Build.VERSION.SDK_INT<= Build.VERSION_CODES.Q && checkSelfPermission(
                    Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) || Build.VERSION.SDK_INT> Build.VERSION_CODES.Q) {
                binding.navigationDrawer.progressBarText.apply {
                    visibility = View.VISIBLE
                    text = "Creating zip..."
                }
                binding.navigationDrawer.progressBar.visibility = View.VISIBLE
            }
            CoroutineScope(Dispatchers.IO).launch {
                ODKProvider.getFormsInteractor().compressToZip(this@AssessmentHomeActivity)
                withContext(Dispatchers.Main){
                    if ((Build.VERSION.SDK_INT<= Build.VERSION_CODES.Q && checkSelfPermission(
                            Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) || Build.VERSION.SDK_INT> Build.VERSION_CODES.Q) {
                        showToast("Finished. Check Downloads folder.")
                        binding.navigationDrawer.progressBarText.visibility = View.GONE
                        binding.navigationDrawer.progressBar.visibility = View.GONE
                    }
                }
            }
        }
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == STORAGE_PERMSSION_REQUEST_CODE) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                onClickDownloadZip()
            } else {
                Toast.makeText(
                    this,
                    com.chatbot.R.string.storage_permission_needed,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun openPrivacyPolicy() {
        startActivity(Intent(this, PrivacyPolicyActivity::class.java))
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_home_toolbar, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.profile -> Toast.makeText(this, getString(R.string.coming_soon), Toast.LENGTH_LONG).show()
        }
        return true
    }

    private fun knowledge() {
        binding.dl.closeDrawer(GravityCompat.START)
        Toast.makeText(this, getString(R.string.coming_soon), Toast.LENGTH_LONG).show()
    }

    private fun help() {
        binding.dl.closeDrawer(GravityCompat.START)
        startActivity(Intent(this, HelpFaqActivity::class.java))
    }

    override fun onDestroy() {
        super.onDestroy()
        CompositeDisposableHelper.destroyCompositeDisposable()
    }

    /* private fun setRedirectionsOnIntent() {
         val intentToUserSelection = Intent(this, AuthenticationActivity::class.java).apply {
             addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
         }
         startActivity(intentToUserSelection)
         finish()
     }
 */

    override fun onBackPressed() {
        super.onBackPressed()
        onBackHandling()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
    }

/*
    private fun handleProgressBarVisibility(visible: Boolean?) {
        if (visible == true) {
            showProgressBar()
        } else {
            binding.swipeRefresh.isRefreshing = false
            hideProgressBar()
        }
    }
*/
}