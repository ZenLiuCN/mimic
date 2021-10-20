/*
 * Copyright 2021 Zen Liu. All Rights Reserved.
 * Licensed under the  GNU LESSER GENERAL PUBLIC LICENSE, Version 2.1 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.gnu.org/licenses/old-licenses/lgpl-2.1.txt
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package cn.zenliu.java.mimic;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Zen.Liu
 * @apiNote
 * @since 2021-10-19
 */
public interface Config {
    /**
     * this effect on all internal Caffeine loading Caches. Must set before Load Mimic class.
     */
    AtomicInteger cacheSize = new AtomicInteger(1024);
}
