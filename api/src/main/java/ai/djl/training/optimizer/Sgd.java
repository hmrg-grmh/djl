/*
 * Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"). You may not use this file except in compliance
 * with the License. A copy of the License is located at
 *
 * http://aws.amazon.com/apache2.0/
 *
 * or in the "license" file accompanying this file. This file is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES
 * OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */
package ai.djl.training.optimizer;

import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDList;
import ai.djl.ndarray.internal.NDArrayEx;
import ai.djl.training.optimizer.learningrate.LearningRateTracker;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** An SGD optimizer. Build with {@link Sgd.Builder}. */
public class Sgd extends Optimizer {

    private LearningRateTracker learningRateTracker;
    private float momentum;
    private boolean lazyUpdate;
    private Map<String, NDArray> momentumStates;

    protected Sgd(Builder builder) {
        super(builder);
        learningRateTracker = builder.getLearningRateTracker();
        momentum = builder.getMomentum();
        lazyUpdate = builder.isLazyUpdate();
        momentumStates = new ConcurrentHashMap<>();
    }

    // TODO: make this protected after integrate with PS store
    @Override
    public void update(String parameterId, NDArray weight, NDArray grad) {
        // TODO: Support Mixed precision Sparse
        float weightDecay = getWeightDecay(parameterId);
        float learningRate = learningRateTracker.getNewLearningRate(updateCount(parameterId));
        NDList inputs;
        // TODO: check momentum correctness
        if (momentum != 0f) {
            inputs =
                    new NDList(
                            weight,
                            grad,
                            momentumStates.computeIfAbsent(parameterId, k -> weight.zerosLike()));
        } else {
            inputs = new NDList(weight, grad);
        }
        NDList weights = new NDList(weight);

        NDArrayEx ex = weight.getNDArrayInternal();
        ex.sgdUpdate(
                inputs,
                weights,
                learningRate,
                weightDecay,
                rescaleGrad,
                clipGrad,
                momentum,
                lazyUpdate);
    }

    public static final class Builder extends BaseBuilder<Builder> {

        private LearningRateTracker learningRateTracker;
        private float momentum;
        private boolean lazyUpdate = true;

        @Override
        protected Builder self() {
            return this;
        }

        public Builder setLearningRateTracker(LearningRateTracker learningRateTracker) {
            this.learningRateTracker = learningRateTracker;
            return this;
        }

        public Builder optMomentum(float momentum) {
            this.momentum = momentum;
            return this;
        }

        public Builder optLazyUpdate(boolean lazyUpdate) {
            this.lazyUpdate = lazyUpdate;
            return this;
        }

        public LearningRateTracker getLearningRateTracker() {
            return learningRateTracker;
        }

        public float getMomentum() {
            return momentum;
        }

        public boolean isLazyUpdate() {
            return lazyUpdate;
        }

        public Sgd build() {
            if (learningRateTracker == null) {
                throw new IllegalArgumentException("No lrTracker set");
            }
            return new Sgd(this);
        }
    }
}