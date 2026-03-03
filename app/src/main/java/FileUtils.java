package com.hfm.app;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;
import java.io.File;
import java.util.List;

public class FileUtils { 

    private static final String TAG = "FileUtils";

    public static boolean deleteFile(Context context, File file) {
        if (file == null || !file.exists()) {
            return true;
        }

        // 1. Handle SD Card Files
        if (StorageUtils.isFileOnSdCard(context, file)) {
            // Try physical deletion via SAF
            if (StorageUtils.deleteFile(context, file)) {
                // SUCCESS: Physical file is gone.
                // FIX: "Blind Delete" the ghost entry. Do NOT query for ID first (causes lockups).
                try {
                    String where = MediaStore.Files.FileColumns.DATA + "=?";
                    String[] selectionArgs = new String[] { file.getAbsolutePath() };
                    context.getContentResolver().delete(MediaStore.Files.getContentUri("external"), where, selectionArgs);
                } catch (Exception e) {
                    Log.w(TAG, "Failed to clean up MediaStore ghost entry: " + e.getMessage());
                }
                return true;
            }
            return false;
        }

        // 2. Handle Internal Storage Files (Standard)
        String path = file.getAbsolutePath();
        ContentResolver resolver = context.getContentResolver();
        String where = MediaStore.Files.FileColumns.DATA + " = ?";
        String[] selectionArgs = new String[]{ path };

        try {
            // Attempt DB delete first
            int rowsDeleted = resolver.delete(MediaStore.Files.getContentUri("external"), where, selectionArgs);
            if (rowsDeleted > 0) {
                // If DB delete worked, check if physical file is gone too
                if (!file.exists()) return true;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error deleting file via ContentResolver", e);
        }

        // Physical fallback for Internal Storage
        if (file.delete()) {
            // REMOVED: context.sendBroadcast(...) to prevent system freeze
            return true;
        }

        return false;
    }

    public static int deleteFileBatch(Context context, List<File> files) {
        if (files == null || files.isEmpty()) return 0;

        int deletedCount = 0;

        // Note: We avoid the bulk "IN (?,?,?)" SQL query here because on Android 11+ 
        // mixing SD card paths and Internal paths in one SQL statement can cause 
        // security exceptions. We process one by one using the optimized deleteFile above.
        
        for (File file : files) {
            if (!file.exists()) {
                deletedCount++;
                continue;
            }

            if (deleteFile(context, file)) {
                deletedCount++;
            }
        }
        
        return deletedCount;
    }
}