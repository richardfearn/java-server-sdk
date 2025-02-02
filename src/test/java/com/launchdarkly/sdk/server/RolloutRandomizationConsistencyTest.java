package com.launchdarkly.sdk.server;

import com.launchdarkly.sdk.EvaluationReason;
import com.launchdarkly.sdk.LDUser;
import com.launchdarkly.sdk.UserAttribute;
import com.launchdarkly.sdk.server.DataModel.FeatureFlag;
import com.launchdarkly.sdk.server.DataModel.Rollout;
import com.launchdarkly.sdk.server.DataModel.RolloutKind;
import com.launchdarkly.sdk.server.DataModel.WeightedVariation;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static com.launchdarkly.sdk.server.EvaluatorTestUtil.BASE_EVALUATOR;
import static com.launchdarkly.sdk.server.EvaluatorTestUtil.expectNoPrerequisiteEvals;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertEquals;

/*
 * Note: These tests are meant to be exact duplicates of tests
 * in other SDKs. Do not change any of the values unless they 
 * are also changed in other SDKs. These are not traditional behavioral 
 * tests so much as consistency tests to guarantee that the implementation 
 * is identical across SDKs.
 */
public class RolloutRandomizationConsistencyTest {
    private Integer noSeed = null;

    private static Rollout buildRollout(boolean isExperiment, boolean untrackedVariations) {
        List<WeightedVariation> variations = new ArrayList<>();
        variations.add(new WeightedVariation(0, 10000, untrackedVariations));
        variations.add(new WeightedVariation(1, 20000, untrackedVariations));
        variations.add(new WeightedVariation(0, 70000, true));
        UserAttribute bucketBy = UserAttribute.KEY;
        RolloutKind kind = isExperiment ? RolloutKind.experiment : RolloutKind.rollout;
        Integer seed = 61;
        Rollout rollout = new Rollout(variations, bucketBy, kind, seed);
        return rollout;
    }
    
    @Test
    public void variationIndexForUserInExperimentTest() {
        // seed here carefully chosen so users fall into different buckets
        Rollout rollout = buildRollout(true, false);
        String key = "hashKey";
        String salt = "saltyA";

        LDUser user1 = new LDUser("userKeyA");
        // bucketVal = 0.09801207
        assertVariationIndexAndExperimentStateForRollout(0, true, rollout, user1, key, salt);

        LDUser user2 = new LDUser("userKeyB");
        // bucketVal = 0.14483777
        assertVariationIndexAndExperimentStateForRollout(1, true, rollout, user2, key, salt);

        LDUser user3 = new LDUser("userKeyC");
        // bucketVal = 0.9242641
        assertVariationIndexAndExperimentStateForRollout(0, false, rollout, user3, key, salt);
    }

    private static void assertVariationIndexAndExperimentStateForRollout(
        int expectedVariation,
        boolean expectedInExperiment,
        Rollout rollout,
        LDUser user,
        String flagKey,
        String salt
        ) {
      FeatureFlag flag = ModelBuilders.flagBuilder(flagKey)
          .on(true)
          .generatedVariations(3)
          .fallthrough(rollout)
          .salt(salt)
          .build();
      EvalResult result = BASE_EVALUATOR.evaluate(flag, user, expectNoPrerequisiteEvals());
      assertThat(result.getVariationIndex(), equalTo(expectedVariation));
      assertThat(result.getReason().getKind(), equalTo(EvaluationReason.Kind.FALLTHROUGH));
      assertThat(result.getReason().isInExperiment(), equalTo(expectedInExperiment));
    }
    
    @Test
    public void bucketUserByKeyTest() {
        LDUser user1 = new LDUser("userKeyA");
        Float point1 = EvaluatorBucketing.bucketUser(noSeed, user1, "hashKey", UserAttribute.KEY, "saltyA");
        assertEquals(0.42157587, point1, 0.0000001);

        LDUser user2 = new LDUser("userKeyB");
        Float point2 = EvaluatorBucketing.bucketUser(noSeed, user2, "hashKey", UserAttribute.KEY, "saltyA");
        assertEquals(0.6708485, point2, 0.0000001);

        LDUser user3 = new LDUser("userKeyC");
        Float point3 = EvaluatorBucketing.bucketUser(noSeed, user3, "hashKey", UserAttribute.KEY, "saltyA");
        assertEquals(0.10343106, point3, 0.0000001);
    }

    @Test
    public void bucketUserWithSeedTest() {
        Integer seed = 61;

        LDUser user1 = new LDUser("userKeyA");
        Float point1 = EvaluatorBucketing.bucketUser(seed, user1, "hashKey", UserAttribute.KEY, "saltyA");
        assertEquals(0.09801207, point1, 0.0000001);

        LDUser user2 = new LDUser("userKeyB");
        Float point2 = EvaluatorBucketing.bucketUser(seed, user2, "hashKey", UserAttribute.KEY, "saltyA");
        assertEquals(0.14483777, point2, 0.0000001);

        LDUser user3 = new LDUser("userKeyC");
        Float point3 = EvaluatorBucketing.bucketUser(seed, user3, "hashKey", UserAttribute.KEY, "saltyA");
        assertEquals(0.9242641, point3, 0.0000001);
    }

}
