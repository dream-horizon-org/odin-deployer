# License Recommendation for Odin Deployer

## Current License

**Odin Deployer** is currently licensed under the **GNU Lesser General Public License v3.0 (LGPL-3.0)**.

This license allows:
- ✅ **Commercial use**: Can be used in commercial products
- ✅ **Modification**: Can modify and distribute modified versions
- ✅ **Distribution**: Can distribute original or modified versions
- ✅ **Patent use**: Grants patent rights from contributors
- ✅ **Private use**: Can use privately without disclosing code

With obligations:
- ⚠️ **Disclose source**: Changes to the library must be open-sourced
- ⚠️ **License and copyright notice**: Must include original license
- ⚠️ **State changes**: Document modifications made
- ⚠️ **Same license**: Modifications to the library must use LGPL-3.0

**Key difference from GPL**: Applications that *use* Odin Deployer (as a dependency or via gRPC) do NOT need to be open-source. Only modifications to Odin Deployer itself must be open-sourced.

---

## Alternative Licenses to Consider

### 1. **MIT License** (Most Permissive)

**When to choose**:
- Maximum adoption and flexibility
- Allow proprietary forks (commercial competitors)
- Minimal legal overhead

**Pros**:
- Simple and widely understood
- No copyleft requirements
- Compatible with all other licenses
- Preferred by corporations

**Cons**:
- Others can create closed-source versions
- No guarantee improvements are contributed back
- Less protection for contributors

**Example projects**: React, Angular, .NET Core, Rails

**License Text**:
```
MIT License

Copyright (c) 2024 Dream11

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

---

### 2. **Apache License 2.0** (Patent Protection)

**When to choose**:
- Need explicit patent grant
- Want to allow proprietary forks
- Contributing to Apache Foundation ecosystem

**Pros**:
- Explicit patent protection
- Allows commercial use
- Requires attribution and changelog
- Well understood by legal teams

**Cons**:
- Slightly more complex than MIT
- Requires NOTICE file

**Example projects**: Kubernetes, Kafka, Hadoop, Elasticsearch (pre-SSPL)

**License Text**: [Full Apache 2.0 text](https://www.apache.org/licenses/LICENSE-2.0.txt)

---

### 3. **GNU Affero General Public License v3.0 (AGPL-3.0)** (Strongest Copyleft)

**When to choose**:
- Prevent SaaS competitors from using code without contributing back
- Ensure all modifications are open-sourced (even if not distributed)
- Strong copyleft philosophy

**Pros**:
- Closes the "SaaS loophole" (network use = distribution)
- Guarantees all improvements are public
- Protects against proprietary cloud services

**Cons**:
- May deter enterprise adoption
- Incompatible with some other licenses
- Complex compliance requirements

**Example projects**: MongoDB (pre-SSPL), Grafana (AGPLv3 for some components)

**Note**: AGPL requires disclosing source code even if the software is only used over a network (e.g., as a SaaS).

---

### 4. **Business Source License (BSL)** / **Server Side Public License (SSPL)** (Delayed Open Source)

**When to choose**:
- Want to prevent cloud providers from offering as-a-service
- Plan to commercialize while still being "source-available"
- Delay true open-source release

**Pros**:
- Source code is public (but not technically "open source")
- Converts to fully open-source license after time period (e.g., 4 years)
- Protects against cloud provider competition

**Cons**:
- Not OSI-approved (not considered "open source")
- May deter contributors
- Complex legal terms

**Example projects**: CockroachDB (BSL), MongoDB (SSPL), MariaDB MaxScale (BSL)

---

## Comparison Table

| License       | Commercial Use | Modify & Distribute | Disclose Source | Patent Grant | Network Use = Distribution |
|---------------|----------------|---------------------|-----------------|--------------|----------------------------|
| **MIT**       | ✅              | ✅                   | ❌               | ❌            | ❌                          |
| **Apache 2.0**| ✅              | ✅                   | ❌               | ✅            | ❌                          |
| **LGPL-3.0** (current) | ✅  | ✅                   | ⚠️ (library changes only) | ✅ | ❌                |
| **AGPL-3.0**  | ✅              | ✅                   | ✅               | ✅            | ✅                          |
| **BSL/SSPL**  | ⚠️ (restrictions) | ✅               | ✅               | ❌            | ✅                          |

---

## Recommendation

**Stick with LGPL-3.0** if:
- You want to encourage enterprise adoption (more permissive than AGPL)
- You want to prevent proprietary forks of *Odin Deployer itself*
- You're okay with companies using it as-a-service without contributing back

**Switch to Apache 2.0** if:
- You want maximum adoption (including by other open-source projects)
- Patent protection is important
- You're fine with proprietary forks

**Switch to AGPL-3.0** if:
- You want to prevent SaaS competitors (e.g., cloud providers offering "Odin-as-a-Service")
- You have a strong copyleft philosophy
- You're willing to accept slower enterprise adoption

**Consider MIT** if:
- You want the simplest, most permissive license
- You want to maximize adoption at all costs
- You don't care about competitors creating closed-source versions

---

## Changing the License

### Process

1. **Verify ownership**: Ensure Dream11 owns all code or has CLAs from contributors
2. **Choose new license**: Based on criteria above
3. **Update LICENSE file**: Replace with new license text
4. **Update headers**: Add new license header to all source files
5. **Update documentation**: README, CONTRIBUTING, etc.
6. **Announce change**: In release notes and on project homepage

### Risks

- **Existing users**: May need to review legal implications
- **Contributors**: May need re-consent if they contributed under old license
- **Downstream projects**: May be impacted if they rely on specific license terms

---

## License Header for Source Files

### LGPL-3.0 Header (Current)

```java
/*
 * Odin Deployer - Deployment orchestration service
 * Copyright (C) 2024 Dream11
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA
 */
```

### Apache 2.0 Header (Alternative)

```java
/*
 * Copyright 2024 Dream11
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
```

### MIT Header (Alternative)

```java
/*
 * MIT License
 *
 * Copyright (c) 2024 Dream11
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
```

---

## Conclusion

The current **LGPL-3.0** license is a **good middle ground**:
- Protects the project from being forked into proprietary versions
- Allows commercial use (including SaaS offerings)
- Encourages contributions back to the core library
- More enterprise-friendly than AGPL

**No change recommended** unless there's a specific business reason (e.g., wanting to prevent SaaS offerings → switch to AGPL, or maximize adoption → switch to Apache 2.0).

---

**Questions?** Contact the maintainer or open a GitHub Discussion.

