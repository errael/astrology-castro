Almost all of the names are the same as the Astrolog names.
Some of the names in these tables are derived. In particular,<br>
`'-'`, `'.'`, `':'`, `' '` are replaced with `'_'`;<br>
`'('`, `')'`, `','` are removed.

The names must have the prefix, for example `S_Taurus`, `S_M_C_`.
There must be at least 3 characters after the prefix; typically that
is unambiguous; in a few cases more characters are required.
`castro` warns/errors if ambiguous and
uses the first one it finds; this is typically what is wanted since
the search is sorted.

Some groups, a group is distinguished by the prefix, have a low priority
section. If there is not a default match, then a low priority match
is used. For example, `H_Whole_EP` is the primary, and `H_W_EP` is the
low priority.

These tables are programatically assembled;
they are extracted from the `Astrolog` source, massaged, and output.

### Signs - prefix S_
|     |     |     |     |     |     |
| --- | --- | --- | --- | --- | --- |
| Aries | Taurus | Gemini | Cancer | Leo | Virgo |
| Libra | Scorpio | Sagittarius | Capricorn | Aquarius | Pisces |

### Months - prefix M_
|     |     |     |     |     |     |
| --- | --- | --- | --- | --- | --- |
| January | February | March | April | May | June |
| July | August | September | October | November | December |

### Days - prefix W_
|     |     |     |     |     |     |
| --- | --- | --- | --- | --- | --- |
| Sunday | Monday | Tuesday | Wednesday | Thursday | Friday |
| Saturday 
### Aspects - prefix A_
|     |     |     |     |     |     |
| --- | --- | --- | --- | --- | --- |
| Con | Opp | Squ | Tri | Sex | Inc |
| SSx | SSq | Ses | Qui | BQn | SQn |
| Sep | Nov | BNv | BSp | TSp | QNv |
| TDc | Un1 | Un2 | Un3 | Un4 | Un5 |
| Par | CPr 
#### Aspects - low priority match
|     |     |     |     |     |     |
| --- | --- | --- | --- | --- | --- |
| Conjunct | Opposite | Square | Trine | Sextile | Inconjunct |
| SemiSextile | SemiSquare | SesquiQuadrate | Quintile | BiQuintile | SemiQuintile |
| Septile | Novile | BiNovile | BiSeptile | TriSeptile | QuatroNovile |
| TreDecile | Undecile | BiUndecile | TriUndecile | QuatroUndecile | QuintUndecile |
| Parallel | ContraParallel | Quincunx | Octile 
### Houses - prefix H_
|     |     |     |     |     |     |
| --- | --- | --- | --- | --- | --- |
| Placidus | Koch | Equal | Campanus | Meridian | Regiomontanus |
| Porphyry | Morinus | Topocentric | Alcabitius | Krusinski | Equal_MC |
| Pullen_S_Ratio | Pullen_S_Delta | Whole | Vedic | Sripati | Horizon |
| APC | Carter_P_Equat_ | Sunshine | Savard_A | Null | Whole_MC |
| Vedic_MC | Equal_Balanced | Whole_Balanced | Vedic_Balanced | Equal_EP | Whole_EP |
| Vedic_EP | Equal_Vertex | Whole_Vertex | Vedic_Vertex | Porphyry_EP | Porphyry_Vertex |
| Pullen_SR_EP | Pullen_SR_Vertex | Pullen_SD_EP | Pullen_SD_Vertex 
#### Houses - low priority match
|     |     |     |     |     |     |
| --- | --- | --- | --- | --- | --- |
| E_Asc | E_MC | P_SR | P_SD | Ratio | Delta |
| S_Ratio | S_Delta | Albategnius | Albategnus | W_Asc | V_Asc |
| W_MC | V_MC | E_Bal | W_Bal | V_Bal | E_EP |
| W_EP | V_EP | E_Ver | W_Ver | V_Ver | P_EP |
| P_Ver | SR_EP | SR_Ver | SD_EP | SD_Ver 
### Objects - prefix O_
#### Planets
|     |     |     |     |     |     |
| --- | --- | --- | --- | --- | --- |
| Earth | Sun | Moon | Mercury | Venus | Mars |
| Jupiter | Saturn | Uranus | Neptune | Pluto 
#### Asteroids
|     |     |     |     |     |     |
| --- | --- | --- | --- | --- | --- |
| Chiron | Ceres | Pallas | Juno | Vesta 
#### Nodes
|     |     |     |     |     |     |
| --- | --- | --- | --- | --- | --- |
| North_Node | South_Node 
#### Others
|     |     |     |     |     |     |
| --- | --- | --- | --- | --- | --- |
| Lilith | Fortune | Vertex | East_Point 
#### Cusps
|     |     |     |     |     |     |
| --- | --- | --- | --- | --- | --- |
| Ascendant | 2nd_Cusp | 3rd_Cusp | Nadir | 5th_Cusp | 6th_Cusp |
| Descendant | 8th_Cusp | 9th_Cusp | Midheaven | 11th_Cusp | 12th_Cusp |

#### Uranians
|     |     |     |     |     |     |
| --- | --- | --- | --- | --- | --- |
| Vulcan | Cupido | Hades | Zeus | Kronos | Apollon |
| Admetos | Vulkanus | Poseidon 
#### Dwarfs
|     |     |     |     |     |     |
| --- | --- | --- | --- | --- | --- |
| Hygiea | Pholus | Eris | Haumea | Makemake | Gonggong |
| Quaoar | Sedna | Orcus 
#### Moons
|     |     |     |     |     |     |
| --- | --- | --- | --- | --- | --- |
| Phobos | Deimos | Ganymede | Callisto | Io | Europa |
| Titan | Rhea | Iapetus | Dione | Tethys | Enceladus |
| Mimas | Hyperion | Titania | Oberon | Umbriel | Ariel |
| Miranda | Triton | Proteus | Nereid | Charon | Hydra |
| Nix | Kerberos | Styx | JupCOB | SatCOB | UraCOB |
| NepCOB | PluCOB 
#### Stars
|     |     |     |     |     |     |
| --- | --- | --- | --- | --- | --- |
| Sirius | Canopus | Rigil_Kent_ | Arcturus | Vega | Capella |
| Rigel | Procyon | Betelgeuse | Achernar | Agena | Altair |
| Acrux | Aldebaran | Antares | Spica | Pollux | Fomalhaut |
| Deneb | Becrux | Regulus | Adara | Castor | Shaula |
| Bellatrix | Gacrux | Alnath | Alnilam | Miaplacidus | Alnair |
| Alioth | Dubhe | Wezen | Kaus_Austr_ | Alkaid | Sargas |
| Menkalinan | Peacock | Alhena | Avior | Murzim | Alphard |
| Polaris | Algol | Suhail | Alcyone | Andromeda | Zeta_Retic_ |
| Galactic_C_ | Great_Attr_ 
#### Extra
|     |     |     |     |     |     |
| --- | --- | --- | --- | --- | --- |
| 1st_Cusp | 4th_Cusp | 7th_Cusp | 10th_Cusp 
#### Objects - low priority match
|     |     |     |     |     |     |
| --- | --- | --- | --- | --- | --- |
| Node | Nod_ | Rahu | Ketu | M_C_ | I_C_ |
| 1st | 4th | 7th | 10th | Vulcanus | Star |
| Orion | Pleiades | M31 | Milky_Way 
