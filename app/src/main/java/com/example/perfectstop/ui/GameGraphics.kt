package com.example.perfectstop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

enum class GameGlyph { TIMER, BLIND, SIGNAL, HISTORY, TROPHY, BLUETOOTH, PERSON, SOUND, HAPTIC, SUN, MOON, BACK, ARROW, EDIT }

/** Authored on a consistent 24-unit grid, with a 1.6-unit stroke. */
@Composable
fun GameIcon(glyph: GameGlyph, modifier: Modifier = Modifier.size(24.dp),
             color: Color = MaterialTheme.colorScheme.onSurface, label: String? = null) {
    Canvas(modifier.then(if (label == null) Modifier else Modifier.semantics { contentDescription = label })) {
        val factor = minOf(size.width, size.height) / 24f
        scale(factor, factor, pivot = Offset.Zero) {
            fun line(vararg points: Float) {
                val path = Path().apply {
                    moveTo(points[0], points[1])
                    for (i in 2 until points.size step 2) lineTo(points[i], points[i + 1])
                }
                drawPath(path, color, style = Stroke(1.6f, cap = StrokeCap.Round))
            }
            fun circle(x: Float, y: Float, r: Float) = drawCircle(color, r, Offset(x, y), style = Stroke(1.6f))
            when (glyph) {
                GameGlyph.TIMER -> { circle(12f,14f,7.5f); line(9f,2f,15f,2f); line(12f,2f,12f,6f); line(12f,14f,15.5f,10f); line(18f,5f,20f,7f) }
                GameGlyph.BLIND -> {
                    val eye = Path().apply { moveTo(2f,12f); cubicTo(7f,4f,17f,4f,22f,12f); cubicTo(17f,20f,7f,20f,2f,12f) }
                    drawPath(eye,color,style=Stroke(1.6f)); circle(12f,12f,3f); line(3f,3f,21f,21f)
                }
                GameGlyph.SIGNAL -> { line(7f,2f,17f,2f,17f,21f,7f,21f,7f,2f); repeat(3) { circle(12f,6f+6f*it,1.5f) } }
                GameGlyph.HISTORY -> { drawArc(color, -135f, 285f, false, Offset(4f,4f), Size(16f,16f), style=Stroke(1.6f)); line(2f,4f,2f,10f,8f,10f); line(12f,7f,12f,12f,16f,14f) }
                GameGlyph.TROPHY -> { line(7f,3f,17f,3f,17f,11f,14f,15f,10f,15f,7f,11f,7f,3f); line(7f,5f,3f,5f,3f,10f,8f,12f); line(17f,5f,21f,5f,21f,10f,16f,12f); line(12f,15f,12f,21f); line(7f,21f,17f,21f) }
                GameGlyph.BLUETOOTH -> line(6f,6f,18f,17f,12f,22f,12f,2f,18f,7f,6f,18f)
                GameGlyph.PERSON -> { circle(12f,7f,3.5f); drawArc(color,180f,180f,false,Offset(4f,13f),Size(16f,14f),style=Stroke(1.6f)) }
                GameGlyph.SOUND -> { line(3f,9f,7f,9f,12f,5f,12f,19f,7f,15f,3f,15f,3f,9f); drawArc(color,-55f,110f,false,Offset(7f,3f),Size(15f,18f),style=Stroke(1.6f)); drawArc(color,-55f,110f,false,Offset(9f,7f),Size(8f,10f),style=Stroke(1.6f)) }
                GameGlyph.HAPTIC -> { line(8f,3f,16f,3f,16f,21f,8f,21f,8f,3f); line(4f,7f,2f,10f,4f,13f,2f,16f); line(20f,7f,22f,10f,20f,13f,22f,16f) }
                GameGlyph.SUN -> { circle(12f,12f,4f); repeat(8) { rotate(it*45f,Offset(12f,12f)) { drawLine(color,Offset(12f,1f),Offset(12f,4f),1.6f,StrokeCap.Round) } } }
                GameGlyph.MOON -> { val path=Path().apply { moveTo(17f,3f); cubicTo(4f,-1f,-1f,18f,12f,21f); cubicTo(17f,22f,21f,18f,22f,14f); cubicTo(11f,18f,8f,7f,17f,3f) }; drawPath(path,color,style=Stroke(1.6f)) }
                GameGlyph.BACK -> { line(20f,12f,4f,12f,10f,6f); line(4f,12f,10f,18f) }
                GameGlyph.ARROW -> { line(4f,12f,20f,12f,14f,6f); line(20f,12f,14f,18f) }
                GameGlyph.EDIT -> { line(4f,16f,16f,4f,20f,8f,8f,20f,4f,20f,4f,16f); line(13f,7f,17f,11f) }
            }
        }
    }
}

@Composable
fun TimingDial(blind: Boolean, modifier: Modifier = Modifier) {
    val ink = MaterialTheme.colorScheme.onSurface
    val subtle = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier) {
        val center = Offset(size.width / 2, size.height / 2)
        val radius = minOf(size.width, size.height) * .43f
        repeat(60) { index ->
            rotate(index * 6f, center) {
                drawLine(if(index % 5 == 0) ink else subtle,
                    Offset(center.x, center.y-radius), Offset(center.x,center.y-radius+if(index%5==0) radius*.15f else radius*.06f),
                    if(index%5==0) 2.dp.toPx() else 1.dp.toPx())
            }
        }
        if (!blind) {
            rotate(42f,center) { drawLine(ink,center,Offset(center.x,center.y-radius*.72f),3.dp.toPx(),StrokeCap.Round) }
            drawCircle(ink,4.dp.toPx(),center)
        } else {
            drawLine(ink,Offset(center.x-radius*.3f,center.y),Offset(center.x+radius*.3f,center.y),3.dp.toPx(),StrokeCap.Round)
        }
    }
}
