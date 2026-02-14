# Performance Learnings and Optimization Records

## ⚡ Bolt's Philosophy
"Speed is a feature. Efficiency is a virtue. Optimize with precision."

## Log of Speed Boosts

### [2026-02-13] N+1 Query Optimization in Player Creation
**Context:** Inserting a player with multiple roles was causing N+1 database inserts (1 for player, N for roles), leading to significant overhead.
**Change:** Implemented a batch insert method `addRolesToPlayer` in `PlayerDao` using `@Insert` with a `List` argument. Updated `PlayerRepository` to use this batch method.
**Impact:**
- **Baseline:** ~1045ms for 50 players (20 roles each).
- **Optimized:** ~216ms for 50 players (20 roles each).
- **Improvement:** ~4.8x faster.
**Key Learning:** Room handles `List` arguments in `@Insert` methods as a single transaction, dramatically reducing IPC and transaction overhead compared to iterative inserts. Always prefer batch operations for related entities.

### [Previous Entry]
...
