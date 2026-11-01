# ComputerCraft (CC: Tweaked peripherals): requests to the Thaumcraft API

The optional CC: Tweaked integration (`docs/deviations/ComputerCraft.md`). The numbers `A11` and `A12` are those of `docs/API_GAPS.md`
section 2. Nothing here was done by reaching into `thaumcraft.common.*`.

## 1. The machines and the golems (A11, A12): answered

`thaumcraft.api.machines` (`IDeconstructor`, `IBrainJar`, `IArcaneEar`, `IArcaneBore`) and `thaumcraft.api.golems` (`GolemApi`,
`GolemView`) are in Thaumcraft 4 Reload since `316897d` (2026-11-01; `thaumcraft_ref` of `gradle.properties` is `c1d8024`, which contains it); the peripherals
`tt_deconstructor`, `tt_braininajar`, `tt_arcaneear`, `tt_arcanebore` and the golem data of `tt_golemconnector` use them
(`docs/deviations/ComputerCraft.md` section 1).

## 2. The label of a jar (P3)

**Need.** 1.7.10 `IAspectContainerImplementation.getAspects` answered a labelled jar (`TileJarFillable.aspectFilter != null`) with the
label's aspect alone, also when the jar was empty; a script could read which aspect an empty labelled jar waits for.

**What the add-on does today.** Nothing: `thaumcraft.api` has no read of a jar's label, so a labelled empty jar answers an empty table
(deviation 5 of `docs/deviations/ComputerCraft.md`). Guessing it from `IEssentiaTransport.getSuctionType` was rejected: an empty jar
whose label was taken off can keep the old aspect as its suction type.

**Proposed.** `@Nullable Aspect BlocksTC.getJarLabel(BlockEntity)` (or an interface `thaumcraft.api.aspects.ILabelledContainer {
@Nullable Aspect getLabel(); }` on the jar block entities), backed by `TileJar.getFilter()`. The same read for a jar item is request 1
of `docs/api-requests/Machines.md`.
