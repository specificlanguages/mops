<?xml version="1.0" encoding="UTF-8"?>
<model ref="r:9363093b-3fa9-4e39-87cb-26240d0efa37(baselanguage.sandbox)">
  <persistence version="9" />
  <languages>
    <use id="f3061a53-9226-4cc5-a443-f952ceaf5816" name="jetbrains.mps.baseLanguage" version="12" />
  </languages>
  <imports>
    <import index="wyt6" ref="6354ebe7-c22a-4a0f-ac54-50b52ab9b065/java:java.lang(JDK/)" implicit="true" />
    <import index="guwi" ref="6354ebe7-c22a-4a0f-ac54-50b52ab9b065/java:java.io(JDK/)" implicit="true" />
  </imports>
  <registry>
    <language id="f3061a53-9226-4cc5-a443-f952ceaf5816" name="jetbrains.mps.baseLanguage">
      <concept id="1202948039474" name="jetbrains.mps.baseLanguage.structure.InstanceMethodCallOperation" flags="nn" index="liA8E" />
      <concept id="1465982738277781862" name="jetbrains.mps.baseLanguage.structure.PlaceholderMember" flags="nn" index="2tJIrI" />
      <concept id="2820489544401957797" name="jetbrains.mps.baseLanguage.structure.DefaultClassCreator" flags="nn" index="HV5vD">
        <reference id="2820489544401957798" name="classifier" index="HV5vE" />
      </concept>
      <concept id="1197027756228" name="jetbrains.mps.baseLanguage.structure.DotExpression" flags="nn" index="2OqwBi">
        <child id="1197027771414" name="operand" index="2Oq$k0" />
        <child id="1197027833540" name="operation" index="2OqNvi" />
      </concept>
      <concept id="1145552977093" name="jetbrains.mps.baseLanguage.structure.GenericNewExpression" flags="nn" index="2ShNRf">
        <child id="1145553007750" name="creator" index="2ShVmc" />
      </concept>
      <concept id="1070475926800" name="jetbrains.mps.baseLanguage.structure.StringLiteral" flags="nn" index="Xl_RD">
        <property id="1070475926801" name="value" index="Xl_RC" />
      </concept>
      <concept id="1081236700938" name="jetbrains.mps.baseLanguage.structure.StaticMethodDeclaration" flags="ig" index="2YIFZL" />
      <concept id="1070533707846" name="jetbrains.mps.baseLanguage.structure.StaticFieldReference" flags="nn" index="10M0yZ">
        <reference id="1144433057691" name="classifier" index="1PxDUh" />
      </concept>
      <concept id="1070534370425" name="jetbrains.mps.baseLanguage.structure.IntegerType" flags="in" index="10Oyi0" />
      <concept id="1068390468198" name="jetbrains.mps.baseLanguage.structure.ClassConcept" flags="ig" index="312cEu" />
      <concept id="1513279640923991009" name="jetbrains.mps.baseLanguage.structure.IGenericClassCreator" flags="ngI" index="366HgL">
        <property id="1513279640906337053" name="inferTypeParams" index="373rjd" />
      </concept>
      <concept id="1068498886296" name="jetbrains.mps.baseLanguage.structure.VariableReference" flags="nn" index="37vLTw">
        <reference id="1068581517664" name="variableDeclaration" index="3cqZAo" />
      </concept>
      <concept id="1068498886292" name="jetbrains.mps.baseLanguage.structure.ParameterDeclaration" flags="ir" index="37vLTG" />
      <concept id="4972933694980447171" name="jetbrains.mps.baseLanguage.structure.BaseVariableDeclaration" flags="ng" index="19Szcq">
        <child id="5680397130376446158" name="type" index="1tU5fm" />
      </concept>
      <concept id="1068580123132" name="jetbrains.mps.baseLanguage.structure.BaseMethodDeclaration" flags="ng" index="3clF44">
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
      <concept id="1068580320020" name="jetbrains.mps.baseLanguage.structure.IntegerConstant" flags="nn" index="3cmrfG">
        <property id="1068580320021" name="value" index="3cmrfH" />
      </concept>
      <concept id="1068581242875" name="jetbrains.mps.baseLanguage.structure.PlusExpression" flags="nn" index="3cpWs3" />
      <concept id="1068581242878" name="jetbrains.mps.baseLanguage.structure.ReturnStatement" flags="nn" index="3cpWs6">
        <child id="1068581517676" name="expression" index="3cqZAk" />
      </concept>
      <concept id="1068581242869" name="jetbrains.mps.baseLanguage.structure.MinusExpression" flags="nn" index="3cpWsd" />
      <concept id="1068581517677" name="jetbrains.mps.baseLanguage.structure.VoidType" flags="in" index="3cqZAl" />
      <concept id="1204053956946" name="jetbrains.mps.baseLanguage.structure.IMethodCall" flags="ngI" index="1ndlxa">
        <reference id="1068499141037" name="baseMethodDeclaration" index="37wK5l" />
        <child id="1068499141038" name="actualArgument" index="37wK5m" />
      </concept>
      <concept id="1107461130800" name="jetbrains.mps.baseLanguage.structure.Classifier" flags="ng" index="3pOWGL">
        <child id="5375687026011219971" name="member" index="jymVt" unordered="true" />
      </concept>
      <concept id="1081773326031" name="jetbrains.mps.baseLanguage.structure.BinaryOperation" flags="nn" index="3uHJSO">
        <child id="1081773367579" name="rightExpression" index="3uHU7w" />
        <child id="1081773367580" name="leftExpression" index="3uHU7B" />
      </concept>
      <concept id="1178549954367" name="jetbrains.mps.baseLanguage.structure.IVisible" flags="ngI" index="1B3ioH">
        <child id="1178549979242" name="visibility" index="1B3o_S" />
      </concept>
      <concept id="1146644602865" name="jetbrains.mps.baseLanguage.structure.PublicVisibility" flags="nn" index="3Tm1VV" />
    </language>
    <language id="ceab5195-25ea-4f22-9b92-103b95ca8c0c" name="jetbrains.mps.lang.core">
      <concept id="1169194658468" name="jetbrains.mps.lang.core.structure.INamedConcept" flags="ngI" index="TrEIO">
        <property id="1169194664001" name="name" index="TrG5h" />
      </concept>
    </language>
  </registry>
  <node concept="312cEu" id="4LxqAFFLDQj">
    <property role="TrG5h" value="Calculator" />
    <node concept="3clFb_" id="4LxqAFFLH2e" role="jymVt">
      <property role="TrG5h" value="add" />
      <node concept="3clFbS" id="4LxqAFFLH2h" role="3clF47">
        <node concept="3clFbF" id="4LxqAFFLH4U" role="3cqZAp">
          <node concept="3cpWs3" id="4LxqAFFLQFg" role="3clFbG">
            <node concept="37vLTw" id="4LxqAFFLQFr" role="3uHU7w">
              <ref role="3cqZAo" node="4LxqAFFLH3b" resolve="b" />
            </node>
            <node concept="37vLTw" id="4LxqAFFLH4T" role="3uHU7B">
              <ref role="3cqZAo" node="4LxqAFFLH2G" resolve="a" />
            </node>
          </node>
        </node>
      </node>
      <node concept="3Tm1VV" id="4LxqAFFLH1S" role="1B3o_S" />
      <node concept="10Oyi0" id="4LxqAFFLH24" role="3clF45" />
      <node concept="37vLTG" id="4LxqAFFLH2G" role="3clF46">
        <property role="TrG5h" value="a" />
        <node concept="10Oyi0" id="4LxqAFFLH2F" role="1tU5fm" />
      </node>
      <node concept="37vLTG" id="4LxqAFFLH3b" role="3clF46">
        <property role="TrG5h" value="b" />
        <node concept="10Oyi0" id="4LxqAFFLH3H" role="1tU5fm" />
      </node>
    </node>
    <node concept="2tJIrI" id="4LxqAFFLRaG" role="jymVt" />
    <node concept="3clFb_" id="4LxqAFFLRca" role="jymVt">
      <property role="TrG5h" value="subtract" />
      <node concept="3clFbS" id="4LxqAFFLRcd" role="3clF47">
        <node concept="3cpWs6" id="4LxqAFFLRLi" role="3cqZAp">
          <node concept="3cpWsd" id="4LxqAFFLRHO" role="3cqZAk">
            <node concept="37vLTw" id="4LxqAFFLRHZ" role="3uHU7w">
              <ref role="3cqZAo" node="4LxqAFFLRdd" resolve="b" />
            </node>
            <node concept="37vLTw" id="4LxqAFFLReA" role="3uHU7B">
              <ref role="3cqZAo" node="4LxqAFFLRdb" resolve="a" />
            </node>
          </node>
        </node>
      </node>
      <node concept="3Tm1VV" id="4LxqAFFLRbq" role="1B3o_S" />
      <node concept="10Oyi0" id="4LxqAFFLRbt" role="3clF45" />
      <node concept="37vLTG" id="4LxqAFFLRdb" role="3clF46">
        <property role="TrG5h" value="a" />
        <node concept="10Oyi0" id="4LxqAFFLRda" role="1tU5fm" />
      </node>
      <node concept="37vLTG" id="4LxqAFFLRdd" role="3clF46">
        <property role="TrG5h" value="b" />
        <node concept="10Oyi0" id="4LxqAFFLRdf" role="1tU5fm" />
      </node>
    </node>
    <node concept="2tJIrI" id="4LxqAFFLRUM" role="jymVt" />
    <node concept="2YIFZL" id="4LxqAFFLSsg" role="jymVt">
      <property role="TrG5h" value="main" />
      <node concept="3clFbS" id="4LxqAFFLSsj" role="3clF47">
        <node concept="3clFbF" id="4LxqAFFLSup" role="3cqZAp">
          <node concept="2OqwBi" id="4LxqAFFLSum" role="3clFbG">
            <node concept="10M0yZ" id="4LxqAFFLSun" role="2Oq$k0">
              <ref role="1PxDUh" to="wyt6:~System" />
              <ref role="3cqZAo" to="wyt6:~System.out" />
            </node>
            <node concept="liA8E" id="4LxqAFFLSuo" role="2OqNvi">
              <ref role="37wK5l" to="guwi:~PrintStream.println(java.lang.String)" resolve="println" />
              <node concept="3cpWs3" id="4LxqAFFLVNX" role="37wK5m">
                <node concept="Xl_RD" id="4LxqAFFLSwN" role="3uHU7B">
                  <property role="Xl_RC" value="2 + 2 = " />
                </node>
                <node concept="2OqwBi" id="4LxqAFFLU_w" role="3uHU7w">
                  <node concept="2ShNRf" id="4LxqAFFLTdz" role="2Oq$k0">
                    <node concept="HV5vD" id="4LxqAFFLUwz" role="2ShVmc">
                      <property role="373rjd" value="true" />
                      <ref role="HV5vE" node="4LxqAFFLDQj" resolve="Calculator" />
                    </node>
                  </node>
                  <node concept="liA8E" id="4LxqAFFLUE3" role="2OqNvi">
                    <ref role="37wK5l" node="4LxqAFFLH2e" resolve="add" />
                    <node concept="3cmrfG" id="4LxqAFFLUGg" role="37wK5m">
                      <property role="3cmrfH" value="2" />
                    </node>
                    <node concept="3cmrfG" id="4LxqAFFLUI7" role="37wK5m">
                      <property role="3cmrfH" value="2" />
                    </node>
                  </node>
                </node>
              </node>
            </node>
          </node>
        </node>
      </node>
      <node concept="3Tm1VV" id="4LxqAFFLRW6" role="1B3o_S" />
      <node concept="3cqZAl" id="4LxqAFFLSrY" role="3clF45" />
    </node>
    <node concept="3Tm1VV" id="4LxqAFFLDQk" role="1B3o_S" />
  </node>
</model>

