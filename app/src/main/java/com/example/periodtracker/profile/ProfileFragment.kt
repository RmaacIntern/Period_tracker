package com.example.periodtracker.profile

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.periodtracker.MainActivity
import com.example.periodtracker.databinding.FragmentProfileBinding
import com.example.periodtracker.notification.AlarmScheduler
import com.example.periodtracker.notification.DailyLogReminderWorker
import com.example.periodtracker.onboarding.OnboardingFragment5
import com.example.periodtracker.profile.sheets.ActivitySheet
import com.example.periodtracker.profile.sheets.AgeSheet
import com.example.periodtracker.profile.sheets.ConditionsSheet
import com.example.periodtracker.profile.sheets.CycleLengthSheet
import com.example.periodtracker.profile.sheets.EditProfileSheet
import com.example.periodtracker.profile.sheets.HeightSheet
import com.example.periodtracker.profile.sheets.LastPeriodSheet
import com.example.periodtracker.profile.sheets.PeriodDurationSheet
import com.example.periodtracker.profile.sheets.WeightSheet
import com.example.periodtracker.viewmodel.CycleViewModel
import com.example.periodtracker.viewmodel.CycleViewModelFactory
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!



    private val vm: CycleViewModel by activityViewModels {
        CycleViewModelFactory(requireActivity().application)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)

        setupUi()
        observeData()
        bindClickListeners()
    }

    // ============================================================
    // UI SETUP
    // ============================================================

    private fun setupUi() {
        setupAvatar()
        setupSwitches()
    }

    private fun setupAvatar() {
        binding.tvAvatar.background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(
                Color.parseColor("#EC4899"),
                Color.parseColor("#A855F7")
            )
        ).apply {
            shape = GradientDrawable.OVAL
        }
    }

    private fun setupSwitches() {

        val pink = Color.parseColor("#EC4899")
        val pinkTrack = Color.parseColor("#FBCFE8")

        val offThumb = Color.parseColor("#D0C8D5")
        val offTrack = Color.parseColor("#EDE6F0")

        val thumbColors = ColorStateList(
            arrayOf(
                intArrayOf(android.R.attr.state_checked),
                intArrayOf()
            ),
            intArrayOf(pink, offThumb)
        )

        val trackColors = ColorStateList(
            arrayOf(
                intArrayOf(android.R.attr.state_checked),
                intArrayOf()
            ),
            intArrayOf(pinkTrack, offTrack)
        )

        listOf(
            binding.switchPeriod,
            binding.switchOvul,
            binding.switchDaily
        ).forEach { switch ->
            switch.thumbTintList = thumbColors
            switch.trackTintList = trackColors
        }
    }

    // ============================================================
    // OBSERVE DATABASE
    // ============================================================

    private fun observeData() {

        val dateFormatter =
            DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())

        val memberSinceFormatter =
            DateTimeFormatter.ofPattern("MMM yyyy", Locale.getDefault())

        vm.settings.observe(viewLifecycleOwner) { settings ->

            settings ?: return@observe

            // ----------------------------------------------------
            // PROFILE HEADER
            // ----------------------------------------------------

            binding.tvAvatar.text =
                settings.userName.firstOrNull()?.uppercase() ?: "S"

            binding.tvProfileName.text = settings.userName

            binding.tvMemberSince.text =
                try {
                    "Member since ${
                        LocalDate.parse(settings.memberSince).format(memberSinceFormatter)
                    }"
                } catch (_: Exception) {
                    "Member since ${settings.memberSince}"
                }

            // ----------------------------------------------------
            // CYCLE SETTINGS
            // ----------------------------------------------------

            binding.tvCycleLengthValue.text   = "${settings.cycleLength} days"
            binding.tvPeriodDurationValue.text = "${settings.periodDuration} days"

            binding.tvLastPeriodValue.text =
                try {
                    LocalDate.parse(settings.lastPeriodStart).format(dateFormatter)
                } catch (_: Exception) {
                    settings.lastPeriodStart
                }

            // ----------------------------------------------------
            // HEALTH PROFILE
            // ----------------------------------------------------

            binding.tvAgeValue.text      = "${settings.age} yrs"
            binding.tvHeightValue.text   = "${settings.heightCm} cm"
            binding.tvWeightValue.text   = "${settings.weightKg.toInt()} kg"
            binding.tvActivityValue.text = settings.activityLevel

            // Conditions: show count summary, not the full comma list
            val condList = settings.conditions
                .split(",")
                .map { it.trim() }
                .filter { it.isNotBlank() }
            binding.tvConditionsValue.text = when {
                condList.isEmpty() -> "None"
                condList.size == 1 -> condList[0]
                else               -> "${condList.size} conditions"
            }

            binding.tvGoalValue.text = settings.goal
            binding.rowActivity.setOnClickListener { showActivitySheet() }
        }
    }

    // ============================================================
    // CLICK LISTENERS
    // ============================================================

    private fun bindClickListeners() {

        // --------------------------------------------------------
        // PROFILE EDIT (name + age + height + weight combined)
        // --------------------------------------------------------

        binding.btnEdit.setOnClickListener {
            showEditProfileSheet()
        }

        // --------------------------------------------------------
        // CYCLE SETTINGS — each card opens its own sheet
        // --------------------------------------------------------

        binding.cardCycleLength.setOnClickListener {
            showCycleLengthSheet()
        }

        binding.cardPeriodDuration.setOnClickListener {
            showPeriodDurationSheet()
        }

        binding.cardLastPeriod.setOnClickListener {
            showLastPeriodSheet()
        }
        binding.btnEditLastPeriod.setOnClickListener { showLastPeriodSheet() }

        // --------------------------------------------------------
        // HEALTH PROFILE — individual rows
        // --------------------------------------------------------

        binding.rowAge.setOnClickListener {
            showAgeSheet()
        }

        binding.rowHeight.setOnClickListener {
            showHeightSheet()
        }

        binding.rowWeight.setOnClickListener {
            showWeightSheet()
        }

        binding.rowConditions.setOnClickListener {
            showConditionsSheet()
        }

        // --------------------------------------------------------
        // PRIVACY
        // --------------------------------------------------------

        binding.rowDeleteData.setOnClickListener {
            showDeleteDialog()
        }

        // --------------------------------------------------------
        // REMINDERS
        // --------------------------------------------------------

        val prefs = requireContext().getSharedPreferences("reminder_prefs", android.content.Context.MODE_PRIVATE)

        binding.switchPeriod.isChecked = prefs.getBoolean("period_reminder", true)
        binding.switchOvul.isChecked   = prefs.getBoolean("ovulation_reminder", true)
        binding.switchDaily.isChecked  = prefs.getBoolean("daily_reminder", true)

        binding.switchPeriod.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("period_reminder", checked).apply()
            if (checked) {
                val date = vm.prediction.value?.nextPeriodDate
                if (date != null) {
                    Log.d("ProfileFragment", "Period alarm ON — scheduling for $date")
                    AlarmScheduler.schedulePeriodAlarms(requireContext(), date)
                } else {
                    Log.w("ProfileFragment", "Period alarm ON — no prediction available, skipping")
                }
            } else {
                Log.d("ProfileFragment", "Period alarm OFF — cancelling")
                AlarmScheduler.cancelPeriodAlarms(requireContext())
            }
        }

        binding.switchOvul.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("ovulation_reminder", checked).apply()
            if (checked) {
                val date = vm.prediction.value?.ovulationDate
                if (date != null) {
                    Log.d("ProfileFragment", "Ovulation alarm ON — scheduling for $date")
                    AlarmScheduler.scheduleOvulationAlarm(requireContext(), date)
                } else {
                    Log.w(
                        "ProfileFragment",
                        "Ovulation alarm ON — no prediction available, skipping"
                    )
                }
            } else {
                Log.d("ProfileFragment", "Ovulation alarm OFF — cancelling")
                AlarmScheduler.cancelOvulationAlarm(requireContext())
            }
        }

        binding.switchDaily.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("daily_reminder", checked).apply()
            if (checked) {
                Log.d("ProfileFragment", "Daily log reminder ON — scheduling WorkManager")
                val request = PeriodicWorkRequestBuilder<DailyLogReminderWorker>(1, TimeUnit.DAYS)
                    .setInitialDelay(calculateDelayUntil8pm(), TimeUnit.MILLISECONDS)
                    .build()
                WorkManager.getInstance(requireContext()).enqueueUniquePeriodicWork(
                    "daily_log_reminder",
                    ExistingPeriodicWorkPolicy.KEEP,
                    request
                )
            } else {
                Log.d("ProfileFragment", "Daily log reminder OFF — cancelling WorkManager")
                WorkManager.getInstance(requireContext()).cancelUniqueWork("daily_log_reminder")
            }
        }
    }

    // ============================================================
    // EDIT PROFILE (combined: name, age, height, weight)
    // ============================================================

    private fun showEditProfileSheet() {

        val settings = vm.settings.value ?: return

        EditProfileSheet(
            currentName     = settings.userName,
            currentAge      = settings.age,
            currentHeightCm = settings.heightCm,
            currentWeightKg = settings.weightKg
        ) { name, age, heightCm, weightKg ->

            vm.updateProfile(name, age, heightCm, weightKg)

        }.show(childFragmentManager, EditProfileSheet.TAG)
    }

    // ============================================================
    // CYCLE LENGTH
    // ============================================================

    private fun showCycleLengthSheet() {

        val currentCycleLength = vm.settings.value?.cycleLength ?: 28

        CycleLengthSheet(currentCycleLength) { newLength ->

            vm.updateCycleLength(newLength)

        }.show(childFragmentManager, CycleLengthSheet.TAG)
    }

    // ============================================================
    // PERIOD DURATION
    // ============================================================

    private fun showPeriodDurationSheet() {

        val currentDuration = vm.settings.value?.periodDuration ?: 5

        PeriodDurationSheet(currentDuration) { newDuration ->

            vm.updatePeriodDuration(newDuration)

        }.show(childFragmentManager, PeriodDurationSheet.TAG)
    }

    // ============================================================
    // LAST PERIOD START
    // ============================================================

    private fun showLastPeriodSheet() {

        val currentDate = vm.settings.value
            ?.lastPeriodStart
            ?.let {
                try { LocalDate.parse(it) } catch (_: Exception) { null }
            }
            ?: LocalDate.now()

        LastPeriodSheet(currentDate) { newDate ->

            vm.updateLastPeriodStart(newDate)

        }.show(childFragmentManager, LastPeriodSheet.TAG)
    }

    // ============================================================
    // AGE  (individual sheet)
    // ============================================================

    private fun showAgeSheet() {

        val currentAge = vm.settings.value?.age ?: 25

        AgeSheet(currentAge) { newAge ->

            val s = vm.settings.value ?: return@AgeSheet
            vm.updateProfile(
                name      = s.userName,
                age       = newAge,
                heightCm  = s.heightCm,
                weightKg  = s.weightKg
            )

        }.show(childFragmentManager, AgeSheet.TAG)
    }

    // ============================================================
    // HEIGHT  (individual sheet)
    // ============================================================

    private fun showHeightSheet() {

        val currentHeight = vm.settings.value?.heightCm ?: 160

        HeightSheet(currentHeight) { newHeight ->

            val s = vm.settings.value ?: return@HeightSheet
            vm.updateProfile(
                name      = s.userName,
                age       = s.age,
                heightCm  = newHeight,
                weightKg  = s.weightKg
            )

        }.show(childFragmentManager, HeightSheet.TAG)
    }

    // ============================================================
    // WEIGHT  (individual sheet)
    // ============================================================

    private fun showWeightSheet() {

        val currentWeight = vm.settings.value?.weightKg ?: 60f

        WeightSheet(currentWeight) { newWeight ->

            val s = vm.settings.value ?: return@WeightSheet
            vm.updateProfile(
                name      = s.userName,
                age       = s.age,
                heightCm  = s.heightCm,
                weightKg  = newWeight
            )

        }.show(childFragmentManager, WeightSheet.TAG)
    }

    // ============================================================
    // CONDITIONS — opens bottom sheet with chips + symptom bars
    // ============================================================

    private fun showConditionsSheet() {
        val logs       = vm.allLogs.value ?: emptyList()
        val conditions = vm.settings.value?.conditions ?: ""
        ConditionsSheet(logs, conditions).show(childFragmentManager, ConditionsSheet.TAG)
    }

    // ============================================================
    // DELETE DATA
    // ============================================================

    private fun showDeleteDialog() {

        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Delete all data?")
            .setMessage(
                "This will permanently erase your cycle history, " +
                        "daily logs, profile information, and settings."
            )
            .setPositiveButton("Delete") { _, _ ->
                vm.deleteAllData()
                requireActivity().finishAffinity()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun calculateDelayUntil8pm(): Long {
        val now = java.time.LocalDateTime.now()
        var target = now.withHour(20).withMinute(0).withSecond(0)
        if (now.isAfter(target)) target = target.plusDays(1)
        return java.time.Duration.between(now, target).toMillis()
    }

    private fun showActivitySheet() {
        val current = vm.settings.value?.activityLevel ?: "BALANCED"
        val level = try {
            OnboardingFragment5.ActivityLevel.valueOf(current)
        } catch (e: Exception) {
            OnboardingFragment5.ActivityLevel.BALANCED
        }
        ActivitySheet(level) { newLevel ->
            vm.updateActivityLevel(newLevel.name)
        }.show(childFragmentManager, ActivitySheet.TAG)
    }




    // ============================================================
    // CLEANUP
    // ============================================================

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }


}