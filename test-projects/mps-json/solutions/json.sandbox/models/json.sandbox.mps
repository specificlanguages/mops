<?xml version="1.0" encoding="UTF-8"?>
<model ref="r:94e02c28-012c-4f06-a2fd-926432934072(json.sandbox)">
  <persistence version="9" />
  <languages>
    <use id="f3f42ddf-d692-4c29-90fb-7360196f01ab" name="com.specificlanguages.json" version="0" />
  </languages>
  <imports />
  <registry>
    <language id="f3f42ddf-d692-4c29-90fb-7360196f01ab" name="com.specificlanguages.json">
      <concept id="2110045694544569294" name="com.specificlanguages.json.structure.JsonString" flags="ng" index="IoS2J">
        <property id="2110045694544569338" name="value" index="IoS2r" />
      </concept>
      <concept id="2110045694544569437" name="com.specificlanguages.json.structure.JsonNumber" flags="ng" index="IoSsW">
        <property id="2110045694544569440" name="value" index="IoSs1" />
      </concept>
      <concept id="2110045694544569357" name="com.specificlanguages.json.structure.JsonArray" flags="ng" index="IoStG">
        <child id="2110045694544569360" name="items" index="IoStL" />
      </concept>
      <concept id="2110045694544566904" name="com.specificlanguages.json.structure.JsonFile" flags="ng" index="IoV$p">
        <child id="2110045694544566910" name="content" index="IoV$v" />
      </concept>
      <concept id="2110045694544567020" name="com.specificlanguages.json.structure.JsonObject" flags="ng" index="IoVAd">
        <child id="2110045694544567028" name="contents" index="IoVAl" />
      </concept>
      <concept id="2110045694544567023" name="com.specificlanguages.json.structure.KeyValuePair" flags="ng" index="IoVAe">
        <child id="2110045694544567026" name="value" index="IoVAj" />
      </concept>
    </language>
    <language id="ceab5195-25ea-4f22-9b92-103b95ca8c0c" name="jetbrains.mps.lang.core">
      <concept id="1169194658468" name="jetbrains.mps.lang.core.structure.INamedConcept" flags="ngI" index="TrEIO">
        <property id="1169194664001" name="name" index="TrG5h" />
      </concept>
    </language>
  </registry>
  <node concept="IoV$p" id="4Twci$d7zxq">
    <property role="TrG5h" value="sandbox" />
    <node concept="IoVAd" id="4Twci$d7zxs" role="IoV$v">
      <node concept="IoVAe" id="4Twci$d7zxu" role="IoVAl">
        <property role="TrG5h" value="string" />
        <node concept="IoS2J" id="4Twci$d7zxw" role="IoVAj">
          <property role="IoS2r" value="foo" />
        </node>
      </node>
      <node concept="IoVAe" id="4Twci$d7zxy" role="IoVAl">
        <property role="TrG5h" value="array" />
        <node concept="IoStG" id="4Twci$d7zx$" role="IoVAj">
          <node concept="IoSsW" id="4Twci$d7zxA" role="IoStL">
            <property role="IoSs1" value="1" />
          </node>
          <node concept="IoSsW" id="4Twci$d7zxC" role="IoStL">
            <property role="IoSs1" value="2" />
          </node>
          <node concept="IoS2J" id="4Twci$d7zxF" role="IoStL">
            <property role="IoS2r" value="x" />
          </node>
        </node>
      </node>
      <node concept="IoVAe" id="4Twci$d7zxI" role="IoVAl">
        <property role="TrG5h" value="object" />
        <node concept="IoVAd" id="4Twci$d7zxK" role="IoVAj">
          <node concept="IoVAe" id="4Twci$d7zxM" role="IoVAl">
            <property role="TrG5h" value="key" />
            <node concept="IoS2J" id="4Twci$d7zxO" role="IoVAj">
              <property role="IoS2r" value="value" />
            </node>
          </node>
        </node>
      </node>
    </node>
  </node>
</model>

