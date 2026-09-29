<?xml version='1.0' encoding='UTF-8'?>
<model ref="r:df8fc5ad-b32f-4109-bfab-79b959f29474(mops.tests.nativecases@tests)">
  <persistence version="9" />
  <languages>
    <use id="f61473f9-130f-42f6-b98d-6c438812c2f6" name="jetbrains.mps.baseLanguage.unitTest" version="1" />
    <use id="f3061a53-9226-4cc5-a443-f952ceaf5816" name="jetbrains.mps.baseLanguage" version="12" />
  </languages>
  <imports />
  <registry>
    <language id="f3061a53-9226-4cc5-a443-f952ceaf5816" name="jetbrains.mps.baseLanguage">
      <concept id="1068580123132" name="jetbrains.mps.baseLanguage.structure.BaseMethodDeclaration" flags="ng" index="3clF44">
        <property id="4276006055363816570" name="isSynchronized" index="od$2w" />
        <property id="1181808852946" name="isFinal" index="DiZV1" />
        <child id="1068580123133" name="returnType" index="3clF45" />
        <child id="1068580123134" name="parameter" index="3clF46" />
        <child id="1068580123135" name="body" index="3clF47" />
      </concept>
      <concept id="1068580123136" name="jetbrains.mps.baseLanguage.structure.StatementList" flags="sn" stub="5293379017992965193" index="3clFbS">
        <child id="1068581517665" name="statement" index="3cqZAp" />
      </concept>
      <concept id="1068581517677" name="jetbrains.mps.baseLanguage.structure.VoidType" flags="in" index="3cqZAl" />
      <concept id="1178549954367" name="jetbrains.mps.baseLanguage.structure.IVisible" flags="ngI" index="1B3ioH">
        <child id="1178549979242" name="visibility" index="1B3o_S" />
      </concept>
      <concept id="1146644602865" name="jetbrains.mps.baseLanguage.structure.PublicVisibility" flags="nn" index="3Tm1VV" />
    </language>
    <language id="f61473f9-130f-42f6-b98d-6c438812c2f6" name="jetbrains.mps.baseLanguage.unitTest">
      <concept id="1171931690126" name="jetbrains.mps.baseLanguage.unitTest.structure.TestMethod" flags="ig" index="3s$Bmu">
        <property id="1171931690128" name="methodName" index="3s$Bm0" />
      </concept>
      <concept id="1171931851043" name="jetbrains.mps.baseLanguage.unitTest.structure.BTestCase" flags="ig" index="3s_ewN">
        <property id="1171931851045" name="testCaseName" index="3s_ewP" />
        <child id="1171931851044" name="testMethodList" index="3s_ewO" />
        <child id="8243879142738613220" name="afterTest" index="1KhZu3" />
        <child id="8243879142738613219" name="beforeTest" index="1KhZu4" />
      </concept>
      <concept id="1171931858461" name="jetbrains.mps.baseLanguage.unitTest.structure.TestMethodList" flags="ng" index="3s_gsd">
        <child id="1171931858462" name="testMethod" index="3s_gse" />
      </concept>
      <concept id="1172017222794" name="jetbrains.mps.baseLanguage.unitTest.structure.Fail" flags="nn" index="3xETmq" />
    </language>
  </registry>
  <node concept="3s_ewN" id="100">
    <property role="3s_ewP" value="NativePass" />
    <node concept="3Tm1VV" id="1001" role="1B3o_S" />
    <node concept="3s_gsd" id="1002" role="3s_ewO">
      <node concept="3s$Bmu" id="101" role="3s_gse">
        <property role="3s$Bm0" value="works" />
        <node concept="3cqZAl" id="1011" role="3clF45" />
        <node concept="3Tm1VV" id="1012" role="1B3o_S" />
        <node concept="3clFbS" id="102" role="3clF47" />
      </node>
    </node>
  </node>
  <node concept="3s_ewN" id="200">
    <property role="3s_ewP" value="NativeFailure" />
    <node concept="3Tm1VV" id="2001" role="1B3o_S" />
    <node concept="3s_gsd" id="2002" role="3s_ewO">
      <node concept="3s$Bmu" id="201" role="3s_gse">
        <property role="3s$Bm0" value="works" />
        <node concept="3cqZAl" id="2011" role="3clF45" />
        <node concept="3Tm1VV" id="2012" role="1B3o_S" />
        <node concept="3clFbS" id="202" role="3clF47">
          <node concept="3xETmq" id="2021" role="3cqZAp" />
        </node>
      </node>
    </node>
  </node>
</model>