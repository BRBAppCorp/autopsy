/*
 * Autopsy Forensic Browser
 *
 * Copyright 2019 Basis Technology Corp.
 * Contact: carrier <at> sleuthkit <dot> org
 *
 * Copyright 2013 Willi Ballenthin
 * Contact: willi.ballenthin <at> gmail <dot> com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.sleuthkit.autopsy.rejview;

import com.williballenthin.rejistry.RegistryKey;
import com.williballenthin.rejistry.RegistryParseException;
import com.williballenthin.rejistry.RegistryValue;

import java.io.UnsupportedEncodingException;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.Logger;

class RejTreeKeyNode implements RejTreeNode {

    private static final Logger logger = Logger.getLogger(RejTreeKeyNode.class.getName());
    private final RegistryKey _key;

    RejTreeKeyNode(RegistryKey key) {
        this._key = key;
    }

    @Override
    public String toString() {
        try {
            return this._key.getName();
        } catch (UnsupportedEncodingException ex) {
            logger.log(Level.WARNING, "Failed to parse key name", ex);
            return "PARSE FAILED.";
        }
    }

    @Override
    public boolean hasChildren() {
        try {
            return this._key.getValueList().size() > 0 || this._key.getSubkeyList().size() > 0;
        } catch (RegistryParseException ex) {
            logger.log(Level.WARNING, "Failed to parse key children.", ex);
            return false;
        }
    }

    @Override
    public List<RejTreeNode> getChildren() {
        LinkedList<RejTreeNode> children = new LinkedList<>();

        try {
            Iterator<RegistryKey> keyit = this._key.getSubkeyList().iterator();
            while (keyit.hasNext()) {
                children.add(new RejTreeKeyNode(keyit.next()));
            }

            Iterator<RegistryValue> valueit = this._key.getValueList().iterator();
            while (valueit.hasNext()) {
                children.add(new RejTreeValueNode(valueit.next()));
            }
        } catch (RegistryParseException ex) {
            logger.log(Level.WARNING, "Failed to parse key children.", ex);
        }
        return children;
    }

    /**
     * @scope: package-protected
     */
    RegistryKey getKey() {
        return this._key;
    }

    /**
     * TODO(wb): this isn't exactly MVC...
     */
    @Override
    public RejTreeNodeView getView() {
        return new RejTreeKeyView(this);
    }
}
