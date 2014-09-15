package com.google.api.ads.adwords.axis.templateengine.extension.shared;

//Copyright 2012 Google Inc. All Rights Reserved.
//
//Licensed under the Apache License, Version 2.0 (the "License");
//you may not use this file except in compliance with the License.
//You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
//Unless required by applicable law or agreed to in writing, software
//distributed under the License is distributed on an "AS IS" BASIS,
//WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//See the License for the specific language governing permissions and
//limitations under the License.

/**
 * @author Mark Bowyer
 * @author Nazmul Idris
 * @version 2.0
 * @since 2/5/13
 */
public interface ConstantsIF {

/** all the files (input, output) are expected to be off of the user's home directory */
FileUtils.Root ROOT_PATH_TYPE = FileUtils.Root.UserHomeDirectory;

/** this is the root path (from the user home directory) that the files are expected in */
static final String ROOTPATH = "";

/** location of the csv file that has all the data */
static final String SOURCE_DATA_FILE = "data.csv";

/** location of the output log file (with errors) */
static final String SUCCESS_LOG_FILE = "success-output.txt";

/** location of the output log file (with successes) */
static final String ERROR_LOG_FILE = "error-output.txt";

/** location of the fix-up csv file (with broken lines to be fixed and retried) */
static final String FIX_UP_FILE = "fix-up.csv";

/** stores the current line that's been processed in the {@link #SOURCE_DATA_FILE} */
static final String EXECUTION_CURSOR_FILE = "current-exection-line.txt";

/** The following are required for OAuth2.0 use */
static final String SCOPE = "https://adwords.google.com/api/adwords";

// This callback URL will allow you to copy the token from the success screen.
static final String CALLBACK_URL = "urn:ietf:wg:oauth:2.0:oob";


/** true means that no mutate operations are made against the AWAPI */
static final boolean DEBUG_MODE = false;

/**
 * this is the number of ms that the program waits (6 mins) in case there are some retryable
 * errors
 */
static final long SLOW_DOWN_TIMEOUT_MS = 6 * 60 * 1000;

/** the program pauses execution after processing the following number of rows/lines */
static final int PROGRAM_PAUSE_AFTER_PROCESSING_RECORD_BLOCK = 100;

/** the program pauses for the following number of ms (1 min) */
static final long PROGRAM_PAUSE_LENGTH_MS = 0; //1 * 1000:

/** the program pauses for the following number of ms after each execution */
static final long PROGRAM_SMALL_PAUSE_LENGTH_MS = (long) (0.4f * 1000f);

/** the maximum lines in a single set of Operations sent to the API */
static final int MAX_OPERATIONS = 5000;

}
