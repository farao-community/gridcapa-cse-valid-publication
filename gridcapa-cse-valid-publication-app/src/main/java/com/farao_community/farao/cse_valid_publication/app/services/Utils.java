/*
 * Copyright (c) 2022, RTE (http://www.rte-france.com)
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.farao_community.farao.cse_valid_publication.app.services;

import org.apache.commons.io.FilenameUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Ameni Walha {@literal <ameni.walha at rte-france.com>}
 */
public final class Utils {

    static int getVersionNumber(String regex, String filename) {
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(FilenameUtils.getName(filename));
        if (matcher.matches()) {
            String s = matcher.group("version");
            return Integer.parseInt(s);
        } else {
            return -1;
        }
    }
}
