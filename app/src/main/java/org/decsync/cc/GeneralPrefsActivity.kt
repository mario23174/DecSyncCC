/**
 * DecSyncCC - GeneralPrefsActivity.kt
 *
 * Copyright (C) 2018 Aldo Gunsing
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, see <http://www.gnu.org/licenses/>.
 */

package org.decsync.cc

import android.Manifest
import android.accounts.Account
import android.accounts.AccountManager
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.CalendarContract
import android.view.MenuItem
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.nononsenseapps.filepicker.FilePickerActivity
import com.nononsenseapps.filepicker.Utils
import org.decsync.cc.contacts.syncAdapterUri
import org.decsync.library.DecsyncException
import org.decsync.library.DecsyncPrefUtils
import org.decsync.library.checkDecsyncInfo
import java.io.File

const val CHOOSE_DECSYNC_DIRECTORY = 0

class GeneralPrefsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        PrefUtils.notifyTheme(this)
        super.onCreate(savedInstanceState)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        setContentView(R.layout.activity_general_prefs)
    }

    override fun onOptionsItemSelected(item: MenuItem) =
            if (item.itemId == android.R.id.home) {
                finish()
                true
            } else {
                false
            }

    @ExperimentalStdlibApi
    class GeneralPrefsFragment : PreferenceFragmentCompat() {

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.general_preferences, rootKey)
        }

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)

            if (PrefUtils.getUseSaf(requireActivity())) {
                initDecsyncDir(true)
                initDecsyncFile(false)
                initDecsyncFileReset(false)
            } else {
                initDecsyncDir(false)
                initDecsyncFile(true)
                initDecsyncFileReset(true)
            }
            initTheme()
            initOfflineSync()
        }

        private fun initDecsyncDir(visible: Boolean) {
            val preference = findPreference<Preference>(DecsyncPrefUtils.DECSYNC_DIRECTORY)!!
            if (visible) {
                preference.setOnPreferenceClickListener {
                    if (!checkDecsyncFine() || !checkCollectionEnabled()) {
                        DecsyncPrefUtils.chooseDecsyncDir(this)
                    }
                    true
                }
            } else {
                preference.isVisible = false
            }
        }

        private fun checkDecsyncFine(): Boolean = try {
            DecsyncPrefUtils.getDecsyncDir(requireActivity())?.let {
                checkDecsyncInfo(requireActivity(), it)
            } ?: throw Exception(getString(R.string.settings_decsync_dir_not_configured))
            true
        } catch (e: Exception) {
            false
        }

        private fun initDecsyncFile(visible: Boolean) {
            val preference = findPreference<Preference>(PrefUtils.DECSYNC_FILE)!!
            if (visible) {
                preference.summary = PrefUtils.getDecsyncFile(requireActivity()).path
                preference.setOnPreferenceClickListener {
                    if (!checkCollectionEnabled()) {
                        val intent = Intent(requireActivity(), FilePickerActivity::class.java)
                        intent.putExtra(FilePickerActivity.EXTRA_ALLOW_CREATE_DIR, true)
                        intent.putExtra(FilePickerActivity.EXTRA_MODE, FilePickerActivity.MODE_DIR)
                        intent.putExtra(FilePickerActivity.EXTRA_START_PATH, PrefUtils.getDecsyncFile(requireActivity()))
                        startActivityForResult(intent, CHOOSE_DECSYNC_DIRECTORY)
                    }
                    true
                }
            } else {
                preference.isVisible = false
            }
        }

        private fun initDecsyncFileReset(visible: Boolean) {
            val preference = findPreference<Preference>(PrefUtils.DECSYNC_FILE_RESET)!!
            if (visible) {
                preference.setOnPreferenceClickListener {
                    if (!checkCollectionEnabled()) {
                        AlertDialog.Builder(requireActivity())
                                .setTitle(R.string.settings_decsync_file_reset_title)
                                .setMessage(getString(R.string.settings_decsync_file_reset_message, PrefUtils.defaultDecsyncDir))
                                .setNegativeButton(android.R.string.no) { _, _ -> }
                                .setPositiveButton(android.R.string.yes) { _, _ ->
                                    setDecsyncFile(File(PrefUtils.defaultDecsyncDir))
                                }
                                .show()
                    }
                    true
                }
            } else {
                preference.isVisible = false
            }
        }

        private fun checkCollectionEnabled(): Boolean {
            val context = requireActivity()

            val addressBookAccounts = AccountManager.get(context).getAccountsByType(getString(R.string.account_type_contacts))
            val anyAddressBookEnabled = addressBookAccounts.isNotEmpty()

            var anyCalendarEnabled = false
            val calendarsAccount = Account(PrefUtils.getCalendarAccountName(context), getString(R.string.account_type_calendars))
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED) {
                context.contentResolver.acquireContentProviderClient(CalendarContract.AUTHORITY)?.let { provider ->
                    anyCalendarEnabled = try {
                        provider.query(syncAdapterUri(calendarsAccount, CalendarContract.Calendars.CONTENT_URI), emptyArray(),
                                null, null, null)!!.use { cursor ->
                            cursor.moveToFirst()
                        }
                    } finally {
                        if (Build.VERSION.SDK_INT >= 24)
                            provider.close()
                        else
                            @Suppress("DEPRECATION")
                            provider.release()
                    }
                }
            }

            return if (anyAddressBookEnabled || anyCalendarEnabled) {
                AlertDialog.Builder(context)
                        .setTitle(R.string.settings_decsync_collections_enabled_title)
                        .setMessage(R.string.settings_decsync_collections_enabled_message)
                        .setNeutralButton(android.R.string.ok) { _, _ -> }
                        .show()
                true
            } else {
                false
            }
        }

        private fun setDecsyncFile(dir: File) {
            val context = requireActivity()
            try {
                checkDecsyncInfo(dir)
                PrefUtils.putDecsyncFile(context, dir)
                findPreference<Preference>(PrefUtils.DECSYNC_FILE)!!.summary = dir.path
            } catch (e: DecsyncException) {
                AlertDialog.Builder(context)
                        .setTitle("DecSync")
                        .setMessage(e.message)
                        .setPositiveButton(android.R.string.ok) { _, _ -> }
                        .show()
            }
        }

        private fun initTheme() {
            val preference = findPreference<Preference>(PrefUtils.THEME)!!
            preference.summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
            preference.setOnPreferenceChangeListener { _, newValue ->
                val mode = Integer.parseInt(newValue as String)
                AppCompatDelegate.setDefaultNightMode(mode)
                true
            }
        }

        private fun initOfflineSync() {
            val context = requireActivity()
            val preference = findPreference<Preference>(PrefUtils.OFFLINE_SYNC)!!
            preference.setOnPreferenceChangeListener { _, enabled ->
                PrefUtils.updateOfflineSync(context, enabled as Boolean)
                true
            }
        }

        override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
            super.onActivityResult(requestCode, resultCode, data)

            if (PrefUtils.getUseSaf(requireActivity())) {
                DecsyncPrefUtils.chooseDecsyncDirResult(requireActivity(), requestCode, resultCode, data)
            } else {
                if (requestCode == CHOOSE_DECSYNC_DIRECTORY) {
                    val uri = data?.data
                    if (resultCode == Activity.RESULT_OK && uri != null) {
                        val oldDir = PrefUtils.getDecsyncFile(requireActivity())
                        val newDir = Utils.getFileForUri(uri)
                        if (oldDir != newDir) {
                            setDecsyncFile(newDir)
                        }
                    }
                }
            }
        }
    }
}
