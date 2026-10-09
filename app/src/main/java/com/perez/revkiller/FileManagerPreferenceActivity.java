package com.perez.revkiller;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.MenuItem;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.CheckBoxPreference;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import com.perez.util.RealFuncUtil;

public class FileManagerPreferenceActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setTitle(R.string.pref_title_settings);
        }

        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(android.R.id.content, new FileManagerSettingsFragment())
                    .commit();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public static class FileManagerSettingsFragment extends PreferenceFragmentCompat {

        public static final String KEY_USE_DEBUG_KEYSTORE = "pref_key_use_debug_keystore";
        public static final String KEY_KEYSTORE_PATH = "pref_key_keystore_path";
        public static final String KEY_KEYSTORE_ALIAS = "pref_key_keystore_alias";
        public static final String KEY_KEYSTORE_ALIAS_PASSWORD = "pref_key_keystore_alias_password";
        public static final String KEY_KEYSTORE_PASSWORD = "pref_key_keystore_password";
        public static final String KEY_SIGN_V1 = "pref_key_sign_v1";
        public static final String KEY_SIGN_V2 = "pref_key_sign_v2";
        public static final String KEY_SIGN_V3 = "pref_key_sign_v3";
        public static final String KEY_SIGN_V4 = "pref_key_sign_v4";
        public static final String KEY_ENABLE_ZIPALIGN = "pref_key_enable_zipalign";

        private CheckBoxPreference useDebugKsPref;
        private Preference ksPathPref;
        private EditTextPreference ksAliasPref;
        private EditTextPreference ksAliasPassPref;
        private EditTextPreference ksPassPref;

        private CheckBoxPreference signV1Pref;
        private CheckBoxPreference signV2Pref;
        private CheckBoxPreference signV3Pref;
        private CheckBoxPreference signV4Pref;

        private ActivityResultLauncher<Intent> keystoreFilePickerLauncher;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            // Register a system file selector callback
            keystoreFilePickerLauncher = registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                            Uri uri = result.getData().getData();
                            if (uri != null) {
                                String resolvedPath;
                                if ("file".equalsIgnoreCase(uri.getScheme())) {
                                    resolvedPath = uri.getPath();
                                } else {
                                    // Extract the actual filename or URI string for the content scheme
                                    String displayName = RealFuncUtil.extractFileNameFromUri(requireContext(), uri);
                                    resolvedPath = !TextUtils.isEmpty(displayName) ? uri.toString() : uri.getPath();
                                }
                                SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(requireContext());
                                sp.edit().putString(KEY_KEYSTORE_PATH, resolvedPath).apply();
                                if (ksPathPref != null) {
                                    ksPathPref.setSummary(resolvedPath);
                                }
                            }
                        }
                    }
            );
        }

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.pref_file_manager, rootKey);

            ListPreference sortByPref = findPreference("pref_key_sort_by");
            if (sortByPref != null) {
                sortByPref.setSummaryProvider(ListPreference.SimpleSummaryProvider.getInstance());
            }

            initKeystorePreferences();
            initSignatureSchemePreferences();
        }

        private void initKeystorePreferences() {
            useDebugKsPref = findPreference(KEY_USE_DEBUG_KEYSTORE);
            ksPathPref = findPreference(KEY_KEYSTORE_PATH);
            ksAliasPref = findPreference(KEY_KEYSTORE_ALIAS);
            ksAliasPassPref = findPreference(KEY_KEYSTORE_ALIAS_PASSWORD);
            ksPassPref = findPreference(KEY_KEYSTORE_PASSWORD);

            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(requireContext());

            // file selector pop-up for the KeyStore file path setting click listener
            if (ksPathPref != null) {
                String curPath = sp.getString(KEY_KEYSTORE_PATH, "");
                if (!TextUtils.isEmpty(curPath)) {
                    ksPathPref.setSummary(curPath);
                }
                ksPathPref.setOnPreferenceClickListener(preference -> {
                    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("*/*");
                    keystoreFilePickerLauncher.launch(intent);
                    return true;
                });
            }

            if (ksAliasPref != null) {
                ksAliasPref.setSummaryProvider(pref -> {
                    String text = ((EditTextPreference) pref).getText();
                    return TextUtils.isEmpty(text) ? getString(R.string.pref_not_set) : text;
                });
            }

            if (ksAliasPassPref != null) {
                ksAliasPassPref.setOnBindEditTextListener(editText ->
                        editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD)
                );
                ksAliasPassPref.setSummaryProvider(pref -> {
                    String text = ((EditTextPreference) pref).getText();
                    return TextUtils.isEmpty(text) ? getString(R.string.pref_not_set) : getString(R.string.pref_password_set);
                });
            }

            if (ksPassPref != null) {
                ksPassPref.setOnBindEditTextListener(editText ->
                        editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD)
                );
                ksPassPref.setSummaryProvider(pref -> {
                    String text = ((EditTextPreference) pref).getText();
                    return TextUtils.isEmpty(text) ? getString(R.string.pref_not_set) : getString(R.string.pref_password_set);
                });
            }

            // Initial state linkage
            boolean useDebug = (useDebugKsPref == null) || useDebugKsPref.isChecked();
            updateCustomKeystorePreferencesState(!useDebug);

            if (useDebugKsPref != null) {
                useDebugKsPref.setOnPreferenceChangeListener((preference, newValue) -> {
                    boolean isChecked = (Boolean) newValue;
                    updateCustomKeystorePreferencesState(!isChecked);
                    return true;
                });
            }
        }

        private void updateCustomKeystorePreferencesState(boolean enabled) {
            if (ksPathPref != null) ksPathPref.setEnabled(enabled);
            if (ksAliasPref != null) ksAliasPref.setEnabled(enabled);
            if (ksAliasPassPref != null) ksAliasPassPref.setEnabled(enabled);
            if (ksPassPref != null) ksPassPref.setEnabled(enabled);
        }

        private void initSignatureSchemePreferences() {
            signV1Pref = findPreference(KEY_SIGN_V1);
            signV2Pref = findPreference(KEY_SIGN_V2);
            signV3Pref = findPreference(KEY_SIGN_V3);
            signV4Pref = findPreference(KEY_SIGN_V4);

            Preference.OnPreferenceChangeListener schemeValidator = (preference, newValue) -> {
                boolean targetChecked = (Boolean) newValue;
                // Must verify that it is the last selected item if uncheck the box
                if (!targetChecked) {
                    int selectedCount = 0;
                    if (signV1Pref != null && (preference == signV1Pref ? targetChecked : signV1Pref.isChecked())) selectedCount++;
                    if (signV2Pref != null && (preference == signV2Pref ? targetChecked : signV2Pref.isChecked())) selectedCount++;
                    if (signV3Pref != null && (preference == signV3Pref ? targetChecked : signV3Pref.isChecked())) selectedCount++;
                    if (signV4Pref != null && (preference == signV4Pref ? targetChecked : signV4Pref.isChecked())) selectedCount++;

                    if (selectedCount == 0) {
                        Toast.makeText(getContext(), R.string.pref_schemes_at_least_one, Toast.LENGTH_SHORT).show();
                        return false; // Refuse to change and force at least one to be selected
                    }
                }
                return true;
            };

            if (signV1Pref != null) signV1Pref.setOnPreferenceChangeListener(schemeValidator);
            if (signV2Pref != null) signV2Pref.setOnPreferenceChangeListener(schemeValidator);
            if (signV3Pref != null) signV3Pref.setOnPreferenceChangeListener(schemeValidator);
            if (signV4Pref != null) signV4Pref.setOnPreferenceChangeListener(schemeValidator);
        }
    }
}