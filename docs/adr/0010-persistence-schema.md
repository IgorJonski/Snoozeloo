---
status: accepted
---

# Persistence: one Room table, a domain-generated text key, partial updates for the ringing path, no migrations before the first release

Everything the app stores is the list of Alarms, and every other piece of state either derives from an Alarm (the Occurrences, the platform scheduler ids) or does not exist yet (there are no app settings in the requirements). So the schema is **one table**, `alarms`, whose row is the `Alarm` with its Snoozed Occurrence folded in as a nullable `snoozed_until` column (ADR-0005), keyed by the `Uuid` the domain generates (ADR-0007), with the Ringtone as the encoded `RingtoneId` string (ADR-0008). The repository offers a whole-row `upsert` for the Alarm Settings screen and two **partial updates** — `setEnabled` and `setSnoozedUntil` — for the paths that write while a screen may be editing the same Alarm (the ringing service, the list toggle), so a Snooze recorded mid-edit is not lost to a stale save. Undo after swipe-to-delete is a hard `DELETE` followed by an `upsert` of the copy the ViewModel kept, which is why `Alarm` carries `createdAt`: the list sorts by Alarm Time then creation, and a restored Alarm returns to its place. The database is set up exactly as the [Room KMP guide](https://developer.android.com/kotlin/multiplatform/room) shows; the schema is exported from version 1 but **no migration is written before the first release** — the version stays at 1, the developer reinstalls, and git holds the history. There is no DataStore.

Decided in [Persistence schema for alarms and snoozed occurrences (#15)](https://github.com/IgorJonski/Snoozeloo/issues/15) on 2026-09-07. Inputs: ADR-0005 (`snoozedUntil` as a column, no occurrence table, scheduler ids derived from `AlarmId`), ADR-0007 (`AlarmId` is a `kotlin.uuid.Uuid`, text primary key), ADR-0008 (`RingtoneId.encode()`), ADR-0004 (module placement). Conventions: the `kmp-data-layer` skill (entity ≠ domain model, mapper named after the target, `Default<Entity>Repository`, no data source pair while Room is the only source).

## Schema (`:component:database:data`, version 1)

| Column | SQL type | Kotlin (entity) | Notes |
| --- | --- | --- | --- |
| `id` | TEXT, primary key | `String` | `AlarmId.toString()`; generated in the domain, never by Room |
| `name` | TEXT, nullable | `String?` | an empty Name is stored as `NULL` (normalised in the domain) |
| `hour` | INTEGER | `Int` | 0–23 |
| `minute` | INTEGER | `Int` | 0–59 |
| `repeat_days` | INTEGER | `Int` | bitmask, bit 0 = Monday (`DayOfWeek.isoDayNumber - 1`); `0` = One-shot |
| `ringtone_id` | TEXT | `String` | `RingtoneId.encode()`: `silent`, `default`, `p:<value>` |
| `volume` | INTEGER | `Int` | 0–100 |
| `vibrate` | INTEGER | `Boolean` | |
| `enabled` | INTEGER | `Boolean` | |
| `snoozed_until` | INTEGER, nullable | `Long?` | epoch milliseconds; the Snoozed Occurrence, `NULL` when none |
| `created_at` | INTEGER | `Long` | epoch milliseconds; sort tie-breaker, preserved by undo |

No `TypeConverter`s: every column is a primitive and `AlarmMapper.kt` does all the conversion (`AlarmEntity.toAlarm()`, `Alarm.toAlarmEntity()`). No indexes beyond the primary key, no foreign keys, no second table. Two request codes on Android and two AlarmKit ids on iOS are pure functions of `id` (ADR-0005, ADR-0007), so nothing about the platform scheduler is stored.

```kotlin
@Entity(tableName = "alarms")
data class AlarmEntity(
    @PrimaryKey val id: String,
    val name: String?,
    val hour: Int,
    val minute: Int,
    @ColumnInfo(name = "repeat_days") val repeatDays: Int,
    @ColumnInfo(name = "ringtone_id") val ringtoneId: String,
    val volume: Int,
    val vibrate: Boolean,
    val enabled: Boolean,
    @ColumnInfo(name = "snoozed_until") val snoozedUntil: Long?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

@Dao
interface AlarmDao {
    @Query("SELECT * FROM alarms ORDER BY hour, minute, created_at") fun observeAll(): Flow<List<AlarmEntity>>
    @Query("SELECT * FROM alarms WHERE id = :id") fun observe(id: String): Flow<AlarmEntity?>
    @Query("SELECT * FROM alarms ORDER BY hour, minute, created_at") suspend fun getAll(): List<AlarmEntity>
    @Query("SELECT * FROM alarms WHERE id = :id") suspend fun get(id: String): AlarmEntity?
    @Upsert suspend fun upsert(alarm: AlarmEntity)
    @Query("UPDATE alarms SET enabled = :enabled, snoozed_until = NULL WHERE id = :id") suspend fun setEnabled(id: String, enabled: Boolean)
    @Query("UPDATE alarms SET snoozed_until = :until WHERE id = :id") suspend fun setSnoozedUntil(id: String, until: Long?)
    @Query("DELETE FROM alarms WHERE id = :id") suspend fun delete(id: String)
}

@Database(entities = [AlarmEntity::class], version = 1, exportSchema = true)
@ConstructedBy(SnoozelooDatabaseConstructor::class)
abstract class SnoozelooDatabase : RoomDatabase() {
    abstract fun alarmDao(): AlarmDao
}

@Suppress("KotlinNoActualForExpect")
expect object SnoozelooDatabaseConstructor : RoomDatabaseConstructor<SnoozelooDatabase>
```

The list order — Alarm Time ascending, creation time as the tie-breaker — is fixed in the DAO query. If the Alarm List spec (#16) picks a different rule, the query changes, not the schema.

## The repository (`:core:alarm:domain`)

```kotlin
interface AlarmRepository {
    fun observeAll(): Flow<List<Alarm>>
    fun observe(id: AlarmId): Flow<Alarm?>
    /** For syncAll(). */
    suspend fun getAll(): List<Alarm>
    suspend fun get(id: AlarmId): Alarm?
    /** Whole row: Alarm Settings save (new or existing) and RestoreAlarm. */
    suspend fun upsert(alarm: Alarm)
    /** Enabled is a scheduling field, so this also clears snoozedUntil, in one statement. */
    suspend fun setEnabled(id: AlarmId, enabled: Boolean)
    /** Snooze (an instant) and Turn Off / housekeeping (null). */
    suspend fun setSnoozedUntil(id: AlarmId, until: Instant?)
    suspend fun delete(id: AlarmId)
}
```

`Alarm` gains `createdAt: Instant`, set by `SaveAlarm` from the injected `Clock` when the Alarm is created and carried unchanged afterwards. The repository stores what it is given: ADR-0005 rule 6 (a change to Alarm Time or Repeat Days drops the Snoozed Occurrence) is enforced by `SaveAlarm`, which compares the stored Alarm with the edited one and nulls `snoozedUntil` before `upsert`. `DefaultAlarmRepository(dao: AlarmDao)` in `:component:alarm:data` maps and delegates; it has no data-source layer because Room is the only source.

**Undo-delete**: `DeleteAlarm` runs `scheduler.cancel(id)` and `repository.delete(id)`; the list ViewModel keeps the deleted `Alarm` in memory for the undo window; `RestoreAlarm` runs `repository.upsert(alarm.copy(snoozedUntil = null))` — same `id`, same `createdAt` — and `scheduler.sync`. If the process dies inside the undo window the Alarm is gone; the window is a few seconds and the trade is accepted. The window's length and what the snackbar looks like are #16.

## Setup

- **Modules**: `:component:database:data` holds `AlarmEntity`, `AlarmDao`, `SnoozelooDatabase`, the constructor `expect`, `expect fun databaseBuilder(): RoomDatabase.Builder<SnoozelooDatabase>` with its two `actual`s, and `databaseModule` (`single { databaseBuilder().setDriver(BundledSQLiteDriver()).setQueryCoroutineContext(Dispatchers.IO).build() }`, `single { get<SnoozelooDatabase>().alarmDao() }`). `:component:alarm:data` holds `AlarmMapper.kt`, `DefaultAlarmRepository` and `alarmDataModule` (`single<AlarmRepository> { DefaultAlarmRepository(get()) }`).
- **Build**: the `snoozeloo.kmp.room` convention plugin (ADR-0004) applies KSP and the `androidx.room3` Gradle plugin, adds `room3-runtime` + `sqlite-bundled` to `commonMain`, the `room3-compiler` to every KSP target and sets `room { schemaDirectory("$projectDir/schemas") }`. Versions: Room 3.0.2, KSP 2.3.11 (#4).
- **File**: `snoozeloo.db`. Android: `context.getDatabasePath("snoozeloo.db")`, the `Context` taken from Koin's `androidContext()`. iOS: `NSDocumentDirectory` via `NSFileManager.defaultManager.URLForDirectory`, as in the guide (the app never enables `UIFileSharingEnabled`, so the directory is not user-visible). Android Auto Backup stays on: a restored database is reconciled with the platform by `syncAll()` on the first start (ADR-0005).
- **Schema export and migrations**: `schemas/…/1.json` is committed from the first commit. Until the first release — the first build handed to anyone but the developer (TestFlight or a Play internal track) — the version stays **1**: a schema change edits the entity in place, regenerates `1.json`, and the developer reinstalls. `fallbackToDestructiveMigration` is never set, so a forgotten migration crashes on upgrade instead of silently deleting alarms. From the first release on, every schema change bumps the version and ships a `Migration` (`@AutoMigration` where Room can derive it) with a migration test.
- **Tests**: `DefaultAlarmRepository` with a Mokkery-mocked `AlarmDao` and `AlarmMapper` round-trips (bitmask, epoch millis, `RingtoneId` encoding, `null` name) in `commonTest`; no DAO test against a real database in this map and no migration test until the first migration.

## Considered options

- **`repeat_days` as a `"MON,WED"` string or seven boolean columns** — rejected: the bitmask is one `Int`, maps to `Set<DayOfWeek>` in two lines, and the mapper test covers it; the string costs parsing, the seven columns cost schema noise.
- **One `minute_of_day` column** — rejected in favour of `hour` + `minute`: readable in the database and 1:1 with `AlarmTime`.
- **Only `upsert(alarm)` on the repository** — rejected: the ringing service and the Alarm Settings screen can write the same row concurrently; a whole-row save from a stale copy would overwrite a Snooze or a One-shot's Disabled state.
- **Soft delete (`deleted_at`) for undo** — rejected: every read and `syncAll()` would have to filter, for the sole benefit of surviving process death inside a few-second window.
- **`created_at` kept only in the entity, preserved by the repository on `upsert`** — rejected: needs an extra read per save and cannot survive a hard delete, which is exactly when undo needs it.
- **A DataStore module from the start** — rejected: nothing to store; #22 adds `DataStore<Preferences>` inside `:component:permissions:data` if it needs a "rationale shown" flag.
- **`Library/Application Support` on iOS** — rejected in favour of the guide's `NSDocumentDirectory`: no user-visible difference without file sharing, one fewer deviation to explain.
- **`fallbackToDestructiveMigration` during development** — rejected: reinstalling costs nothing pre-release, and the flag would hide a missing migration after release.
- **Room `autoGenerate` `Long` key** — rejected by ADR-0007: the AlarmKit id *is* the `AlarmId`, so the domain must own it.

## Consequences

- `Alarm` (`:core:alarm:domain`) is `Alarm(id, name?, time: AlarmTime, repeatDays: RepeatDays, ringtoneId: RingtoneId, volume: Volume, vibrate, enabled, snoozedUntil: Instant?, createdAt: Instant)`; `AlarmRepository` has the shape above (amends ADR-0004).
- `SetAlarmEnabled` calls `repository.setEnabled` then `scheduler.sync(get(id))`; `SnoozeAlarm` and `TurnOffAlarm` (ADR-0006) call `setSnoozedUntil`, and `TurnOffAlarm` calls `setEnabled(id, false)` for a One-shot; `syncAll()` housekeeping (a past `snoozedUntil`, an iOS One-shot AlarmKit dropped) uses the same two partial updates.
- The Alarm List's order is a DAO concern; #16 only confirms or changes the `ORDER BY`.
- The first post-release schema change is the first migration, and the first migration test; `schemas/…/1.json` is the baseline it diffs against.
- Nothing in the app needs `DataStore`; #22 owns that decision if a permission-rationale flag appears.
