/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package subjectreco.evaluator.ir;

import com.google.common.collect.Lists;
import org.apache.mahout.cf.taste.common.TasteException;
import org.apache.mahout.cf.taste.eval.RelevantItemsDataSplitter;
import org.apache.mahout.cf.taste.impl.common.FastByIDMap;
import org.apache.mahout.cf.taste.impl.common.FastIDSet;
import org.apache.mahout.cf.taste.impl.model.GenericUserPreferenceArray;
import org.apache.mahout.cf.taste.model.DataModel;
import org.apache.mahout.cf.taste.model.Preference;
import org.apache.mahout.cf.taste.model.PreferenceArray;

import java.util.List;

/**
 * Picks relevant items to be those with the strongest preference, and
 * includes the other users' preferences in full.
 *
 * Custom version that picks up specific courses.
 */
public final class MyRelevantItemsDataSplitter implements RelevantItemsDataSplitter {

  @Override
  public FastIDSet getRelevantItemsIDs(long userID,
                                       int at,
                                       double relevanceThreshold,
                                       DataModel dataModel) throws TasteException {
    PreferenceArray prefs = dataModel.getPreferencesFromUser(userID);
    FastIDSet relevantItemIDs = new FastIDSet(at);
    prefs.sortByValueReversed();
    for (int i = 0; i < prefs.length(); i++) {
      if (i==0 || i == 3 || i == 5 || i == (prefs.length()/2) -2 || i == prefs.length()/2 || i == (prefs.length()/2)+2 || i == prefs.length()-5 || i == prefs.length()-1 ) {
        relevantItemIDs.add(prefs.getItemID(i));
      }
    }
    return relevantItemIDs;
  }

  @Override
  public void processOtherUser(long userID,
                               FastIDSet relevantItemIDs,
                               FastByIDMap<PreferenceArray> trainingUsers,
                               long otherUserID,
                               DataModel dataModel) throws TasteException {
    PreferenceArray prefs2Array = dataModel.getPreferencesFromUser(otherUserID);
    // If we're dealing with the very user that we're evaluating for precision/recall,
    if (userID == otherUserID) {
      // then must remove all the test IDs, the "relevant" item IDs
      List<Preference> prefs2 = Lists.newArrayListWithCapacity(prefs2Array.length());
      for (Preference pref : prefs2Array) {
        prefs2.add(pref);
      }
      prefs2.removeIf(pref -> relevantItemIDs.contains(pref.getItemID()));
      if (!prefs2.isEmpty()) {
        trainingUsers.put(otherUserID, new GenericUserPreferenceArray(prefs2));
      }
    } else {
      // otherwise just add all those other user's prefs
      trainingUsers.put(otherUserID, prefs2Array);
    }
  }
}
