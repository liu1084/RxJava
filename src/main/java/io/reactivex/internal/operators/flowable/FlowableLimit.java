/**
 * Copyright (c) 2016-present, RxJava Contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See
 * the License for the specific language governing permissions and limitations under the License.
 */

package io.reactivex.internal.operators.flowable;

import java.util.concurrent.atomic.AtomicLong;

import org.reactivestreams.*;

import io.reactivex.*;
import io.reactivex.internal.subscriptions.*;
import io.reactivex.plugins.RxJavaPlugins;

/**
 * Limits both the total request amount and items received from the upstream.
 * <p>History: 2.1.6 - experimental
 * @param <T> the source and output value type
 * @since 2.2
 */
public final class FlowableLimit<T> extends AbstractFlowableWithUpstream<T, T> {

    final long n;

    public FlowableLimit(Flowable<T> source, long n) {
        super(source);
        this.n = n;
    }

    @Override
    protected void subscribeActual(Subscriber<? super T> s) {
        source.subscribe(new LimitSubscriber<T>(s, n));
    }

    static final class LimitSubscriber<T>
    extends AtomicLong
    implements FlowableSubscriber<T>, Subscription {

        private static final long serialVersionUID = 2288246011222124525L;

        final Subscriber<? super T> downstream;

        long remaining;

        Subscription upstream;

        LimitSubscriber(Subscriber<? super T> actual, long remaining) {
            this.downstream = actual;
            this.remaining = remaining;
            lazySet(remaining);
        }

        @Override
        public void onSubscribe(Subscription s) {
            if (SubscriptionHelper.validate(this.upstream, s)) {
                if (remaining == 0L) {
                    s.cancel();
                    EmptySubscription.complete(downstream);
                } else {
                    this.upstream = s;
                    downstream.onSubscribe(this);
                }
            }
        }

        @Override
        public void onNext(T t) {
            long r = remaining;
            if (r > 0L) {
                remaining = --r;
                downstream.onNext(t);
                if (r == 0L) {
                    upstream.cancel();
                    downstream.onComplete();
                }
            }
        }

        @Override
        public void onError(Throwable t) {
            if (remaining > 0L) {
                remaining = 0L;
                downstream.onError(t);
            } else {
                RxJavaPlugins.onError(t);
            }
        }

        @Override
        public void onComplete() {
            if (remaining > 0L) {
                remaining = 0L;
                downstream.onComplete();
            }
        }

        @Override
        public void request(long n) {
            if (SubscriptionHelper.validate(n)) {
                for (;;) {
                    long r = get();
                    if (r == 0L) {
                        break;
                    }
                    long toRequest;
                    if (r <= n) {
                        toRequest = r;
                    } else {
                        toRequest = n;
                    }
                    long u = r - toRequest;
                    if (compareAndSet(r, u)) {
                        upstream.request(toRequest);
                        break;
                    }
                }
            }
        }

        @Override
        public void cancel() {
            upstream.cancel();
        }

    }
}
