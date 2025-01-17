/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fpm.data

import android.net.Uri
import org.mozilla.fpm.models.Backup

interface BackupRepository : Repository<Backup, String> {
    fun import(fileUri: Uri, fileName: String)
    fun deploy(name: String)
    fun getFileSignature(path: String): String?
}
