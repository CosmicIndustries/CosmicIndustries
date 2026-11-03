# Flight-Dynamics Outputs: YASim + JSBSim

**Status:** Required product capability; not implemented in Build 001.  
**Parent product:** Cosmic 3DGS / Aircraft Engineering Workbench.  
**Purpose:** Produce native, inspectable flight-dynamics model files for both FlightGear YASim and JSBSim from a shared aircraft engineering record.

## 1. Product requirement

The workbench must create both:

1. **YASim aircraft configuration** — FlightGear's geometry-oriented aircraft FDM input.
2. **JSBSim aircraft configuration** — JSBSim-ML XML, with associated engine, thruster/propeller, system and control files as needed.

These are independent outputs. The application must not treat one generated file as a conversion of the other or silently equate their aerodynamic models.

## 2. Source-of-truth architecture

Do not author either simulator format directly from a Gaussian splat. A splat is a visual reconstruction and may help recover shape, but it does not establish mass, inertia, airfoil polars, control effectiveness, propulsion maps, or measured aerodynamic coefficients.

Use a simulator-independent **Aircraft Flight-Dynamics Profile** linked to a versioned aircraft master:

- Geometry revision and coordinate-frame definition.
- Reference dimensions and units, with source and confidence for each measurement.
- Wing, horizontal-tail, vertical-tail and fuselage geometry.
- Airfoil section identity and section/polar data where available.
- Flaps, ailerons, elevator, rudder, trim and other control-surface geometry and limits.
- Mass properties: empty mass, operating mass, maximum takeoff mass, center of gravity envelope, fuel stations and inertia tensor.
- Landing gear: contact points, steering, suspension, compression and braking data.
- Propulsion: engine model, rated power/thrust, RPM limits, propeller/thruster geometry and performance curves.
- Flight-control mapping, control direction, travel, rates, mixing and trim.
- Flight-test or validated reference data: stall, cruise, climb, approach, takeoff, glide and fuel-flow points.
- Provenance, uncertainty, validation state and revision references for all inputs.

Every numeric input must carry a unit. Unknown values remain unknown; they must not be replaced with plausible-looking defaults.

## 3. Separate backend responsibilities

### 3.1 YASim exporter

Generate a native YASim XML configuration from geometric and physical inputs, including:

- Aircraft mass and center of gravity.
- Wing and tail surfaces, sections, incidence, dihedral, sweep and control surfaces.
- Fuselage stations/sections.
- Engine and thruster/propeller definitions.
- Fuel tanks and landing gear.
- Approach and cruise conditions used by YASim's solver.

The exporter must preserve the YASim coordinate convention in its backend adapter and explicitly transform from the workbench's canonical coordinate frame. The FlightGear YASim tools must be used to solve and inspect the generated configuration; a file that merely parses as XML is not a validated flight model.

### 3.2 JSBSim exporter

Generate JSBSim-ML XML, using the installed/selected JSBSim schema version. The exporter must account for the distinct JSBSim model sections:

- `fileheader`, `metrics`, and `mass_balance`.
- `ground_reactions`.
- `propulsion`, with referenced engine and thruster/propeller definitions.
- `flight_control` and optional `system` / `autopilot`.
- `aerodynamics`.
- `input` and `output` definitions as required by the target integration.

Aerodynamic coefficient functions and tables must come from documented source data, validated flight-test data, CFD/wind-tunnel analysis, or an explicitly labeled approximation. The generator must record the source and validity range of each coefficient dataset.

### 3.3 Shared capabilities, not shared assumptions

The two exporters may share geometry, units, mass properties, propulsion source data and control definitions. They must retain separate backend transformations, configuration schemas, coefficient representations and validation reports.

## 4. Validation gates

A generated file is not automatically a flight-ready aircraft model.

Each export has explicit status:

- **Incomplete:** required source data are missing.
- **Draft:** XML generated, but simulator validation has not passed.
- **Solver-valid:** backend parser/solver accepts the model.
- **Trim-checked:** required trim conditions converge and outputs are recorded.
- **Regression-tested:** selected operating points match documented reference tolerances.
- **Engineering-reviewed:** assumptions, provenance and test results reviewed by a qualified human.

Required automated checks:

1. Schema/XML validation for the selected JSBSim format version.
2. YASim solver run and capture of convergence/solution output.
3. JSBSim load, trim and scripted operating-point tests.
4. Unit and coordinate transform checks.
5. Mass, CG, inertia and control-direction sanity checks.
6. Regression test fixtures with expected values and tolerances.
7. Export manifest recording source revision, generator version, simulator version, input provenance, unresolved items and validation results.

No output may be labeled flight-ready or airworthy. These are simulation artifacts and require engineering verification; they are not approved aircraft design data.

## 5. Suggested project outputs

```text
Exports/
├── FlightGear/
│   └── <aircraft-id>/
│       ├── <aircraft>-set.xml
│       ├── <aircraft>-yasim.xml
│       ├── Models/
│       └── export-manifest.json
└── JSBSim/
    └── <aircraft-id>/
        ├── <aircraft>.xml
        ├── Engines/
        ├── Systems/
        └── export-manifest.json
```

Exact packaging and referenced file locations must follow the target FlightGear/JSBSim installation and version.

## 6. Implementation sequence

- **FD-01 — Canonical FDM data model:** typed units, coordinate frames, mass, geometry, propulsion, controls, aero data and provenance.
- **FD-02 — Geometry and mass-property capture:** measured dimensions, reference stations, CG and inertia inputs, validation of coordinate transforms.
- **FD-03 — YASim exporter:** schema-aware XML generation plus YASim solver integration.
- **FD-04 — JSBSim exporter:** JSBSim-ML generation plus referenced engine/thruster/control files and schema validation.
- **FD-05 — Validation harness:** solver, trim, performance-point and regression tests for both backends.
- **FD-06 — UI and export packaging:** project screens for missing inputs, draft previews, validation reports and reproducible export bundles.

## 7. Build 001 boundary

Build 001 is only the offline project foundation. It does **not** implement either FDM exporter. YASim and JSBSim are mandatory subsequent capabilities, not claims about the current APK.
