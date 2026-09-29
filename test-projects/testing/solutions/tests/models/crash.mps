<?xml version="1.0" encoding="UTF-8"?>
<model ref="r:f063b80c-afd0-456f-a54d-110f477062b3(mops.tests.crash)">
  <persistence version="9" />
  <languages>
    <use id="f3061a53-9226-4cc5-a443-f952ceaf5816" name="jetbrains.mps.baseLanguage" version="12" />
  </languages>
  <imports>
    <import index="yqm7" ref="63b449db-0918-4a4a-a891-2c430ab133e4/java:org.junit.jupiter.api(org.junit.junit5/)" />
    <import index="wyt6" ref="6354ebe7-c22a-4a0f-ac54-50b52ab9b065/java:java.lang(JDK/)" />
  </imports>
  <registry>
    <language id="f3061a53-9226-4cc5-a443-f952ceaf5816" name="jetbrains.mps.baseLanguage">
      <concept id="1202948039474" name="jetbrains.mps.baseLanguage.structure.InstanceMethodCallOperation" flags="nn" index="liA8E" />
      <concept id="1188207840427" name="jetbrains.mps.baseLanguage.structure.AnnotationInstance" flags="nn" index="2AHcQZ">
        <reference id="1188208074048" name="annotation" index="2AI5Lk" />
        <child id="1188214630783" name="value" index="2B76xF" />
      </concept>
      <concept id="1188208481402" name="jetbrains.mps.baseLanguage.structure.HasAnnotation" flags="ngI" index="2AJDlI">
        <child id="1188208488637" name="annotation" index="2AJF6D" />
      </concept>
      <concept id="1188214545140" name="jetbrains.mps.baseLanguage.structure.AnnotationInstanceValue" flags="ng" index="2B6LJw">
        <reference id="1188214555875" name="key" index="2B6OnR" />
        <child id="1188214607812" name="value" index="2B70Vg" />
      </concept>
      <concept id="1197027756228" name="jetbrains.mps.baseLanguage.structure.DotExpression" flags="nn" index="2OqwBi">
        <child id="1197027771414" name="operand" index="2Oq$k0" />
        <child id="1197027833540" name="operation" index="2OqNvi" />
      </concept>
      <concept id="1081236700937" name="jetbrains.mps.baseLanguage.structure.StaticMethodCall" flags="nn" index="2YIFZM">
        <reference id="1144433194310" name="classConcept" index="1Pybhc" />
      </concept>
      <concept id="1068390468198" name="jetbrains.mps.baseLanguage.structure.ClassConcept" flags="ig" index="312cEu" />
      <concept id="1068580123132" name="jetbrains.mps.baseLanguage.structure.BaseMethodDeclaration" flags="ng" index="3clF44">
        <child id="1068580123133" name="returnType" index="3clF45" />
        <child id="1068580123135" name="body" index="3clF47" />
      </concept>
      <concept id="1068580123165" name="jetbrains.mps.baseLanguage.structure.InstanceMethodDeclaration" flags="ig" index="3clFb_" />
      <concept id="1068580123155" name="jetbrains.mps.baseLanguage.structure.ExpressionStatement" flags="nn" index="3clFbF">
        <child id="1068580123156" name="expression" index="3clFbG" />
      </concept>
      <concept id="1068580123136" name="jetbrains.mps.baseLanguage.structure.StatementList" flags="sn" stub="5293379017992965193" index="3clFbS">
        <child id="1068581517665" name="statement" index="3cqZAp" />
      </concept>
      <concept id="1068580320020" name="jetbrains.mps.baseLanguage.structure.IntegerConstant" flags="nn" index="3cmrfG">
        <property id="1068580320021" name="value" index="3cmrfH" />
      </concept>
      <concept id="1068581517677" name="jetbrains.mps.baseLanguage.structure.VoidType" flags="in" index="3cqZAl" />
      <concept id="1204053956946" name="jetbrains.mps.baseLanguage.structure.IMethodCall" flags="ngI" index="1ndlxa">
        <reference id="1068499141037" name="baseMethodDeclaration" index="37wK5l" />
        <child id="1068499141038" name="actualArgument" index="37wK5m" />
      </concept>
      <concept id="1107461130800" name="jetbrains.mps.baseLanguage.structure.Classifier" flags="ng" index="3pOWGL">
        <child id="5375687026011219971" name="member" index="jymVt" unordered="true" />
      </concept>
      <concept id="1178549954367" name="jetbrains.mps.baseLanguage.structure.IVisible" flags="ngI" index="1B3ioH">
        <child id="1178549979242" name="visibility" index="1B3o_S" />
      </concept>
      <concept id="1146644602865" name="jetbrains.mps.baseLanguage.structure.PublicVisibility" flags="nn" index="3Tm1VV" />
      <concept id="1116615150612" name="jetbrains.mps.baseLanguage.structure.ClassifierClassExpression" flags="nn" index="3VsKOn">
        <reference id="1116615189566" name="classifier" index="3VsUkX" />
      </concept>
    </language>
    <language id="ceab5195-25ea-4f22-9b92-103b95ca8c0c" name="jetbrains.mps.lang.core">
      <concept id="1169194658468" name="jetbrains.mps.lang.core.structure.INamedConcept" flags="ngI" index="TrEIO">
        <property id="1169194664001" name="name" index="TrG5h" />
      </concept>
    </language>
  </registry>
  <node concept="312cEu" id="1fwLXRCtMP9">
    <property role="TrG5h" value="CrashTest" />
    <node concept="3Tm1VV" id="1fwLXRCtMPa" role="1B3o_S" />
    <node concept="2AHcQZ" id="1fwLXRCtMPb" role="2AJF6D">
      <ref role="2AI5Lk" to="yqm7:~TestMethodOrder" resolve="TestMethodOrder" />
      <node concept="2B6LJw" id="1fwLXRCtMPc" role="2B76xF">
        <ref role="2B6OnR" to="yqm7:~TestMethodOrder.value()" resolve="value" />
        <node concept="3VsKOn" id="1fwLXRCtMPe" role="2B70Vg">
          <ref role="3VsUkX" to="yqm7:~MethodOrderer$MethodName" resolve="MethodOrderer.MethodName" />
        </node>
      </node>
    </node>
    <node concept="3clFb_" id="1fwLXRCtMPf" role="jymVt">
      <property role="TrG5h" value="aCompleted" />
      <node concept="2AHcQZ" id="1fwLXRCtMPg" role="2AJF6D">
        <ref role="2AI5Lk" to="yqm7:~Test" resolve="Test" />
      </node>
      <node concept="3clFbS" id="1fwLXRCtMPh" role="3clF47" />
      <node concept="3Tm1VV" id="1fwLXRCtMPi" role="1B3o_S" />
      <node concept="3cqZAl" id="1fwLXRCtMPj" role="3clF45" />
    </node>
    <node concept="3clFb_" id="1fwLXRCtMPk" role="jymVt">
      <property role="TrG5h" value="zCrash" />
      <node concept="2AHcQZ" id="1fwLXRCtMPl" role="2AJF6D">
        <ref role="2AI5Lk" to="yqm7:~Test" resolve="Test" />
      </node>
      <node concept="3clFbS" id="1fwLXRCtMPm" role="3clF47">
        <node concept="3clFbF" id="1fwLXRCtMPn" role="3cqZAp">
          <node concept="2OqwBi" id="1fwLXRCtMPO" role="3clFbG">
            <node concept="2YIFZM" id="1fwLXRCtMPB" role="2Oq$k0">
              <ref role="1Pybhc" to="wyt6:~Runtime" resolve="Runtime" />
              <ref role="37wK5l" to="wyt6:~Runtime.getRuntime()" resolve="getRuntime" />
            </node>
            <node concept="liA8E" id="1fwLXRCtMPP" role="2OqNvi">
              <ref role="37wK5l" to="wyt6:~Runtime.halt(int)" resolve="halt" />
              <node concept="3cmrfG" id="1fwLXRCtMPQ" role="37wK5m">
                <property role="3cmrfH" value="23" />
              </node>
            </node>
          </node>
        </node>
      </node>
      <node concept="3Tm1VV" id="1fwLXRCtMPr" role="1B3o_S" />
      <node concept="3cqZAl" id="1fwLXRCtMPs" role="3clF45" />
    </node>
  </node>
</model>

