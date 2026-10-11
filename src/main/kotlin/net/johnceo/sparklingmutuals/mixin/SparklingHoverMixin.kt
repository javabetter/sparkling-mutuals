package net.johnceo.sparklingmutuals.mixin

import com.llamalad7.mixinextras.injector.wrapoperation.Operation
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation
import net.johnceo.sparklingmutuals.commands.SparklingLookup
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.FormattedText
import net.minecraft.util.FormattedCharSequence
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At

/** Chat hovers wrap at half the screen; the `/sparkling` hover is a laid-out table, so for it (only) wrapping is off. */
@Mixin(GuiGraphicsExtractor::class)
class SparklingHoverMixin {
    @WrapOperation(method = ["componentHoverEffect"], at = [At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/Font;split(Lnet/minecraft/network/chat/FormattedText;I)Ljava/util/List;")])
    private fun noWrapForTables(font: Font, text: FormattedText, width: Int, original: Operation<List<FormattedCharSequence>>): List<FormattedCharSequence> =
        original.call(font, text, if (SparklingLookup.isWide(text)) maxOf(width, 4000) else width)
}
