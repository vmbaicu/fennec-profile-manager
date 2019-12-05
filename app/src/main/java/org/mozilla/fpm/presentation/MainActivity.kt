/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fpm.presentation

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.ContextThemeWrapper
import android.view.View
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.android.synthetic.main.activity_main.*
import org.mozilla.fpm.BuildConfig
import org.mozilla.fpm.R
import org.mozilla.fpm.data.BackupRepositoryImpl
import org.mozilla.fpm.data.PrefsManager
import org.mozilla.fpm.models.Backup
import org.mozilla.fpm.presentation.mvp.MainContract
import org.mozilla.fpm.presentation.mvp.MainPresenter
import org.mozilla.fpm.utils.PermissionUtils.Companion.checkStoragePermission
import org.mozilla.fpm.utils.PermissionUtils.Companion.validateStoragePermissionOrShowRationale
import org.mozilla.fpm.utils.Utils.Companion.makeFirefoxPackageContext
import org.mozilla.fpm.utils.Utils.Companion.showMessage

class MainActivity : AppCompatActivity(), MainContract.View, BackupsRVAdapter.MenuListener {
    private lateinit var presenter: MainPresenter
    private lateinit var adapter: BackupsRVAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)
        BackupRepositoryImpl.setContext(applicationContext)
        title = getString(R.string.app_name_full)
        presenter = MainPresenter()
        presenter.attachView(this@MainActivity)

        adapter = BackupsRVAdapter()
        backups_rv.layoutManager = LinearLayoutManager(this)
        backups_rv.addItemDecoration(
            DividerItemDecoration(
                this,
                LinearLayoutManager.VERTICAL
            )
        )
        backups_rv.adapter = adapter
        refresh_layout.isEnabled = false

        if (PrefsManager.checkFirstRun()) showFirstrun()

        if (checkStoragePermission(this, BACKUPS_STORAGE_REQUEST_CODE)) presenter.getBackups()

        create_fab.setOnClickListener {
            attemptCreate()
            hideFirstrun()
        }
        import_fab.setOnClickListener {
            if (checkStoragePermission(this, IMPORT_STORAGE_REQUEST_CODE)) {
                presenter.importBackup()
                hideFirstrun()
            }
        }
    }

    override fun onBackupsLoaded(data: List<Backup>) {
        if (data.isEmpty()) prompt.visibility = View.VISIBLE else {
            prompt.visibility = View.GONE
            adapter.updateData(data)
            adapter.setListener(this@MainActivity)
        }
    }

    override fun onBackupCreated(backup: Backup) {
        prompt.visibility = View.GONE
        adapter.add(backup)
        adapter.setListener(this@MainActivity)
    }

    override fun onApplyClick(item: Backup) {
        if (makeFirefoxPackageContext(this) == null) {
            showMessage(this, getString(R.string.error_shareduserid, BuildConfig.FIREFOX_PACKAGE_NAME))
            return
        }

        val builder = AlertDialog.Builder(ContextThemeWrapper(this, R.style.AlertDialogTheme))
        builder.setTitle(getString(R.string.warning_title))
        builder.setMessage(getString(R.string.warning_message))
        builder.setPositiveButton(getString(R.string.yes)) { _, _ -> presenter.applyBackup(item.name) }
        builder.setNegativeButton(getString(R.string.no), null)
        builder.show()
    }

    override fun onShareClick(item: Backup) {
        TODO("not implemented")
    }

    @SuppressLint("InflateParams")
    override fun onEditClick(item: Backup, position: Int) {
        val builder = AlertDialog.Builder(ContextThemeWrapper(this, R.style.AlertDialogTheme))
        val inflater = layoutInflater
        builder.setTitle(getString(R.string.edit_backup_name))
        val dialogLayout = inflater.inflate(R.layout.alert_input, null)
        val input = dialogLayout.findViewById<EditText>(R.id.input)
        input.setText(item.name.replace(".zip", ""))
        builder.setView(dialogLayout)
        builder.setPositiveButton(getString(R.string.ok)) { _, _ ->
            run {
                if (input.text.isEmpty()) {
                    showMessage(this@MainActivity, getString(R.string.error_input_null))
                    return@setPositiveButton
                }

                presenter.renameBackup(item, input.text.toString())
                adapter.update(Backup(input.text.toString(), item.createdAt), position)
            }
        }
        builder.setNegativeButton(getString(R.string.cancel), null)
        builder.show()
    }

    override fun onDeleteClick(item: Backup, position: Int) {
        presenter.deleteBackup(item.name)
        adapter.delete(position)

        if (adapter.itemCount == 0) prompt.visibility = View.VISIBLE
    }

    override fun showFirstrun() {
        create_label.visibility = View.GONE
        import_label.visibility = View.VISIBLE
        PrefsManager.setFirstRunComplete()
    }

    override fun hideFirstrun() {
        create_label.visibility = View.GONE
        import_label.visibility = View.GONE
    }

    override fun showLoading() {
        refresh_layout.isRefreshing = true
    }

    override fun hideLoading() {
        refresh_layout.isRefreshing = false
    }

    override fun onBackupApplied() {
        showMessage(this, getString(R.string.backup_applied))
    }

    override fun onDestroy() {
        super.onDestroy()
        presenter.detachView()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        when (requestCode) {
            BACKUPS_STORAGE_REQUEST_CODE -> {
                if (validateStoragePermissionOrShowRationale(
                        this,
                        permissions,
                        grantResults
                    )
                ) presenter.getBackups()
            }

            CREATE_STORAGE_REQUEST_CODE -> {
                if (validateStoragePermissionOrShowRationale(
                        this,
                        permissions,
                        grantResults
                    )
                ) attemptCreate()
            }

            IMPORT_STORAGE_REQUEST_CODE -> {
                if (validateStoragePermissionOrShowRationale(
                        this,
                        permissions,
                        grantResults
                    )
                ) presenter.importBackup()
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    @SuppressLint("InflateParams")
    fun attemptCreate() {
        if (makeFirefoxPackageContext(this) == null) {
            showMessage(this, getString(R.string.error_shareduserid, BuildConfig.FIREFOX_PACKAGE_NAME))
            return
        }

        if (!checkStoragePermission(this, CREATE_STORAGE_REQUEST_CODE)) return

        val builder = AlertDialog.Builder(ContextThemeWrapper(this, R.style.AlertDialogTheme))
        val inflater = layoutInflater
        builder.setTitle(getString(R.string.set_backup_name))
        val dialogLayout = inflater.inflate(R.layout.alert_input, null)
        val input = dialogLayout.findViewById<EditText>(R.id.input)
        builder.setView(dialogLayout)
        builder.setPositiveButton(getString(R.string.ok)) { _, _ ->
            run {
                if (input.text.isEmpty()) {
                    showMessage(this@MainActivity, getString(R.string.error_input_null))
                    return@setPositiveButton
                }

                presenter.createBackup(input.text.toString())
            }
        }
        builder.setNegativeButton(getString(R.string.cancel), null)
        builder.show()
    }

    companion object {
        private const val BACKUPS_STORAGE_REQUEST_CODE = 1001
        private const val CREATE_STORAGE_REQUEST_CODE = 1002
        private const val IMPORT_STORAGE_REQUEST_CODE = 1003
    }
}
