package com.example.bamia.ui

/**
 * UI에서 얼굴 박스, 표정, 눈/웃음 확률 등을 표시할지 여부를 결정하는 플래그들을 저장하는 객체.
 */
object ExpressionDisplayConfig {
    var showFaceBox: Boolean = true
    var showExpression: Boolean = true
    var showEyeProbability: Boolean = true
    var showSmileProbability: Boolean = true
}
