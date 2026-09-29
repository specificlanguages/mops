<?xml version='1.0' encoding='UTF-8'?>
<model ref="r:43f9baa2-56ba-4e40-bd07-e9f5fc496eea(mops.tests.ordinary)">
  <persistence version="9" />
  <languages>
    <use id="f3061a53-9226-4cc5-a443-f952ceaf5816" name="jetbrains.mps.baseLanguage" version="12" />
  </languages>
  <imports>
    <import index="junit" ref="49808fad-9d41-4b96-83fa-9231640f6b2b/java:junit.framework(JUnit/)" />
    <import index="yqm7" ref="63b449db-0918-4a4a-a891-2c430ab133e4/java:org.junit.jupiter.api(org.junit.junit5/)" />
    <import index="wyt6" ref="6354ebe7-c22a-4a0f-ac54-50b52ab9b065/java:java.lang(JDK/)" />
    <import index="tphd" ref="63b449db-0918-4a4a-a891-2c430ab133e4/java:org.junit.jupiter.params(org.junit.junit5/)" />
    <import index="c35q" ref="63b449db-0918-4a4a-a891-2c430ab133e4/java:org.junit.jupiter.params.provider(org.junit.junit5/)" />
  </imports>
  <registry>
    <language id="f3061a53-9226-4cc5-a443-f952ceaf5816" name="jetbrains.mps.baseLanguage">
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
      <concept id="1188220165133" name="jetbrains.mps.baseLanguage.structure.ArrayLiteral" flags="nn" index="2BsdOp">
        <child id="1188220173759" name="item" index="2BsfMF" />
      </concept>
      <concept id="1145552977093" name="jetbrains.mps.baseLanguage.structure.GenericNewExpression" flags="nn" index="2ShNRf">
        <child id="1145553007750" name="creator" index="2ShVmc" />
      </concept>
      <concept id="1070475926800" name="jetbrains.mps.baseLanguage.structure.StringLiteral" flags="nn" index="Xl_RD">
        <property id="1070475926801" name="value" index="Xl_RC" />
      </concept>
      <concept id="1081236700938" name="jetbrains.mps.baseLanguage.structure.StaticMethodDeclaration" flags="ig" index="2YIFZL" />
      <concept id="1081236700937" name="jetbrains.mps.baseLanguage.structure.StaticMethodCall" flags="nn" index="2YIFZM">
        <reference id="1144433194310" name="classConcept" index="1Pybhc" />
      </concept>
      <concept id="1164991038168" name="jetbrains.mps.baseLanguage.structure.ThrowStatement" flags="nn" index="YS8fn">
        <child id="1164991057263" name="throwable" index="YScLw" />
      </concept>
      <concept id="1070534370425" name="jetbrains.mps.baseLanguage.structure.IntegerType" flags="in" index="10Oyi0" />
      <concept id="1068390468198" name="jetbrains.mps.baseLanguage.structure.ClassConcept" flags="ig" index="312cEu">
        <child id="1165602531693" name="superclass" index="1zkMxy" />
      </concept>
      <concept id="1068498886292" name="jetbrains.mps.baseLanguage.structure.ParameterDeclaration" flags="ir" index="37vLTG" />
      <concept id="4972933694980447171" name="jetbrains.mps.baseLanguage.structure.BaseVariableDeclaration" flags="ng" index="19Szcq">
        <child id="5680397130376446158" name="type" index="1tU5fm" />
      </concept>
      <concept id="1068580123132" name="jetbrains.mps.baseLanguage.structure.BaseMethodDeclaration" flags="ng" index="3clF44">
        <child id="1164879685961" name="throwsItem" index="Sfmx6" />
        <child id="1068580123133" name="returnType" index="3clF45" />
        <child id="1068580123134" name="parameter" index="3clF46" />
        <child id="1068580123135" name="body" index="3clF47" />
      </concept>
      <concept id="1068580123165" name="jetbrains.mps.baseLanguage.structure.InstanceMethodDeclaration" flags="ig" index="3clFb_" />
      <concept id="1068580123155" name="jetbrains.mps.baseLanguage.structure.ExpressionStatement" flags="nn" index="3clFbF">
        <child id="1068580123156" name="expression" index="3clFbG" />
      </concept>
      <concept id="1068580123136" name="jetbrains.mps.baseLanguage.structure.StatementList" flags="sn" stub="5293379017992965193" index="3clFbS">
        <child id="1068581517665" name="statement" index="3cqZAp" />
      </concept>
      <concept id="1068580123137" name="jetbrains.mps.baseLanguage.structure.BooleanConstant" flags="nn" index="3clFbT" />
      <concept id="1068580320020" name="jetbrains.mps.baseLanguage.structure.IntegerConstant" flags="nn" index="3cmrfG">
        <property id="1068580320021" name="value" index="3cmrfH" />
      </concept>
      <concept id="1068581517677" name="jetbrains.mps.baseLanguage.structure.VoidType" flags="in" index="3cqZAl" />
      <concept id="1204053956946" name="jetbrains.mps.baseLanguage.structure.IMethodCall" flags="ngI" index="1ndlxa">
        <reference id="1068499141037" name="baseMethodDeclaration" index="37wK5l" />
        <child id="1068499141038" name="actualArgument" index="37wK5m" />
      </concept>
      <concept id="1212685548494" name="jetbrains.mps.baseLanguage.structure.ClassCreator" flags="nn" index="1pGfFk" />
      <concept id="1107461130800" name="jetbrains.mps.baseLanguage.structure.Classifier" flags="ng" index="3pOWGL">
        <child id="5375687026011219971" name="member" index="jymVt" unordered="true" />
      </concept>
      <concept id="1107535904670" name="jetbrains.mps.baseLanguage.structure.ClassifierType" flags="in" index="3uibUv">
        <reference id="1107535924139" name="classifier" index="3uigEE" />
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
  <node concept="312cEu" id="1fwLXRCtEju">
    <property role="TrG5h" value="OrdinaryPass" />
    <node concept="3Tm1VV" id="1fwLXRCtEjv" role="1B3o_S" />
    <node concept="3clFb_" id="1fwLXRCtEjw" role="jymVt">
      <property role="TrG5h" value="passing" />
      <node concept="2AHcQZ" id="1fwLXRCtEjx" role="2AJF6D">
        <ref role="2AI5Lk" to="yqm7:~Test" resolve="Test" />
      </node>
      <node concept="3clFbS" id="1fwLXRCtEjy" role="3clF47" />
      <node concept="3Tm1VV" id="1fwLXRCtEjz" role="1B3o_S" />
      <node concept="3cqZAl" id="1fwLXRCtEj$" role="3clF45" />
    </node>
    <node concept="3clFb_" id="1fwLXRCtEj_" role="jymVt">
      <property role="TrG5h" value="skipped" />
      <node concept="2AHcQZ" id="1fwLXRCtEjA" role="2AJF6D">
        <ref role="2AI5Lk" to="yqm7:~Test" resolve="Test" />
      </node>
      <node concept="2AHcQZ" id="1fwLXRCtEjB" role="2AJF6D">
        <ref role="2AI5Lk" to="yqm7:~Disabled" resolve="Disabled" />
        <node concept="2B6LJw" id="1fwLXRCtEjC" role="2B76xF">
          <ref role="2B6OnR" to="yqm7:~Disabled.value()" resolve="value" />
          <node concept="Xl_RD" id="1fwLXRCtEjD" role="2B70Vg">
            <property role="Xl_RC" value="fixture skip" />
          </node>
        </node>
      </node>
      <node concept="3clFbS" id="1fwLXRCtEjE" role="3clF47" />
      <node concept="3Tm1VV" id="1fwLXRCtEjF" role="1B3o_S" />
      <node concept="3cqZAl" id="1fwLXRCtEjG" role="3clF45" />
    </node>
    <node concept="3clFb_" id="1fwLXRCtEjH" role="jymVt">
      <property role="TrG5h" value="aborted" />
      <node concept="2AHcQZ" id="1fwLXRCtEjI" role="2AJF6D">
        <ref role="2AI5Lk" to="yqm7:~Test" resolve="Test" />
      </node>
      <node concept="3clFbS" id="1fwLXRCtEjJ" role="3clF47">
        <node concept="3clFbF" id="1fwLXRCtEjK" role="3cqZAp">
          <node concept="2YIFZM" id="1fwLXRCtJWI" role="3clFbG">
            <ref role="1Pybhc" to="yqm7:~Assumptions" resolve="Assumptions" />
            <ref role="37wK5l" to="yqm7:~Assumptions.assumeTrue(boolean)" resolve="assumeTrue" />
            <node concept="3clFbT" id="1fwLXRCtJWJ" role="37wK5m" />
          </node>
        </node>
      </node>
      <node concept="3Tm1VV" id="1fwLXRCtEjN" role="1B3o_S" />
      <node concept="3cqZAl" id="1fwLXRCtEjO" role="3clF45" />
    </node>
  </node>
  <node concept="312cEu" id="1fwLXRCtEjP">
    <property role="TrG5h" value="OrdinaryFailure" />
    <node concept="3Tm1VV" id="1fwLXRCtEjQ" role="1B3o_S" />
    <node concept="3clFb_" id="1fwLXRCtEjR" role="jymVt">
      <property role="TrG5h" value="failing" />
      <node concept="2AHcQZ" id="1fwLXRCtEjS" role="2AJF6D">
        <ref role="2AI5Lk" to="yqm7:~Test" resolve="Test" />
      </node>
      <node concept="3clFbS" id="1fwLXRCtEjT" role="3clF47">
        <node concept="YS8fn" id="1fwLXRCtEjW" role="3cqZAp">
          <node concept="2ShNRf" id="1fwLXRCtJWK" role="YScLw">
            <node concept="1pGfFk" id="1fwLXRCtLB_" role="2ShVmc">
              <ref role="37wK5l" to="wyt6:~AssertionError.&lt;init&gt;(java.lang.Object)" resolve="AssertionError" />
              <node concept="Xl_RD" id="1fwLXRCtLBA" role="37wK5m">
                <property role="Xl_RC" value="fixture failure" />
              </node>
            </node>
          </node>
        </node>
      </node>
      <node concept="3Tm1VV" id="1fwLXRCtEjX" role="1B3o_S" />
      <node concept="3cqZAl" id="1fwLXRCtEjY" role="3clF45" />
    </node>
    <node concept="3clFb_" id="1fwLXRCtEjZ" role="jymVt">
      <property role="TrG5h" value="stillRuns" />
      <node concept="2AHcQZ" id="1fwLXRCtEk0" role="2AJF6D">
        <ref role="2AI5Lk" to="yqm7:~Test" resolve="Test" />
      </node>
      <node concept="3clFbS" id="1fwLXRCtEk1" role="3clF47" />
      <node concept="3Tm1VV" id="1fwLXRCtEk2" role="1B3o_S" />
      <node concept="3cqZAl" id="1fwLXRCtEk3" role="3clF45" />
    </node>
  </node>
  <node concept="312cEu" id="1fwLXRCtEk4">
    <property role="TrG5h" value="ContainerFailure" />
    <node concept="3Tm1VV" id="1fwLXRCtEk5" role="1B3o_S" />
    <node concept="2YIFZL" id="1fwLXRCtEk6" role="jymVt">
      <property role="TrG5h" value="setup" />
      <node concept="2AHcQZ" id="1fwLXRCtEk7" role="2AJF6D">
        <ref role="2AI5Lk" to="yqm7:~BeforeAll" resolve="BeforeAll" />
      </node>
      <node concept="3clFbS" id="1fwLXRCtEk8" role="3clF47">
        <node concept="YS8fn" id="1fwLXRCtEkb" role="3cqZAp">
          <node concept="2ShNRf" id="1fwLXRCtLBB" role="YScLw">
            <node concept="1pGfFk" id="1fwLXRCtLTN" role="2ShVmc">
              <ref role="37wK5l" to="wyt6:~RuntimeException.&lt;init&gt;(java.lang.String)" resolve="RuntimeException" />
              <node concept="Xl_RD" id="1fwLXRCtLTO" role="37wK5m">
                <property role="Xl_RC" value="fixture setup failure" />
              </node>
            </node>
          </node>
        </node>
      </node>
      <node concept="3Tm1VV" id="1fwLXRCtEkc" role="1B3o_S" />
      <node concept="3cqZAl" id="1fwLXRCtEkd" role="3clF45" />
    </node>
    <node concept="3clFb_" id="1fwLXRCtEke" role="jymVt">
      <property role="TrG5h" value="neverRuns" />
      <node concept="2AHcQZ" id="1fwLXRCtEkf" role="2AJF6D">
        <ref role="2AI5Lk" to="yqm7:~Test" resolve="Test" />
      </node>
      <node concept="3clFbS" id="1fwLXRCtEkg" role="3clF47" />
      <node concept="3Tm1VV" id="1fwLXRCtEkh" role="1B3o_S" />
      <node concept="3cqZAl" id="1fwLXRCtEki" role="3clF45" />
    </node>
  </node>
  <node concept="312cEu" id="1fwLXRCtEku">
    <property role="TrG5h" value="LegacyTest" />
    <node concept="3Tm1VV" id="1fwLXRCtEkv" role="1B3o_S" />
    <node concept="3uibUv" id="1fwLXRCtEkw" role="1zkMxy">
      <ref role="3uigEE" to="junit:~TestCase" resolve="TestCase" />
    </node>
    <node concept="3clFb_" id="1fwLXRCtEkx" role="jymVt">
      <property role="TrG5h" value="testLegacy" />
      <node concept="3clFbS" id="1fwLXRCtEky" role="3clF47" />
      <node concept="3Tm1VV" id="1fwLXRCtEkz" role="1B3o_S" />
      <node concept="3cqZAl" id="1fwLXRCtEk$" role="3clF45" />
    </node>
  </node>
  <node concept="312cEu" id="1fwLXRCtLVC">
    <property role="TrG5h" value="ParameterizedCase" />
    <node concept="3Tm1VV" id="1fwLXRCtLVD" role="1B3o_S" />
    <node concept="3clFb_" id="1fwLXRCtLVE" role="jymVt">
      <property role="TrG5h" value="parameterized" />
      <node concept="2AHcQZ" id="1fwLXRCtLVF" role="2AJF6D">
        <ref role="2AI5Lk" to="tphd:~ParameterizedTest" resolve="ParameterizedTest" />
      </node>
      <node concept="2AHcQZ" id="1fwLXRCtLVG" role="2AJF6D">
        <ref role="2AI5Lk" to="c35q:~ValueSource" resolve="ValueSource" />
        <node concept="2B6LJw" id="1fwLXRCtLVH" role="2B76xF">
          <ref role="2B6OnR" to="c35q:~ValueSource.ints()" resolve="ints" />
          <node concept="2BsdOp" id="1fwLXRCtLVK" role="2B70Vg">
            <node concept="3cmrfG" id="1fwLXRCtLVI" role="2BsfMF">
              <property role="3cmrfH" value="1" />
            </node>
            <node concept="3cmrfG" id="1fwLXRCtLVJ" role="2BsfMF">
              <property role="3cmrfH" value="2" />
            </node>
          </node>
        </node>
      </node>
      <node concept="37vLTG" id="1fwLXRCtLVL" role="3clF46">
        <property role="TrG5h" value="value" />
        <node concept="10Oyi0" id="1fwLXRCtLVM" role="1tU5fm" />
      </node>
      <node concept="3clFbS" id="1fwLXRCtLVN" role="3clF47" />
      <node concept="3Tm1VV" id="1fwLXRCtLVO" role="1B3o_S" />
      <node concept="3cqZAl" id="1fwLXRCtLVP" role="3clF45" />
    </node>
  </node>
</model>