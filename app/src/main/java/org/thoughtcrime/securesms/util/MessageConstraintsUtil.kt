package org.thoughtcrime.securesms.util

import org.thoughtcrime.securesms.database.model.MessageRecord
import org.thoughtcrime.securesms.recipients.Recipient
import org.thoughtcrime.securesms.recipients.RecipientId
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.hours

/**
 * Helpers for determining if a message send/receive is valid for those that
 * have strict time limits.
 */
object MessageConstraintsUtil {
  private val RECEIVE_THRESHOLD = TimeUnit.DAYS.toMillis(1)
  private val SEND_THRESHOLD = TimeUnit.HOURS.toMillis(3)

  private val MAX_EDIT_COUNT = 10

  @JvmStatic
  fun isValidRemoteDeleteReceive(targetMessage: MessageRecord, deleteSenderId: RecipientId, deleteServerTimestamp: Long): Boolean {
    val selfIsDeleteSender = isSelf(deleteSenderId)

    val isValidIncomingOutgoing = selfIsDeleteSender && targetMessage.isOutgoing || !selfIsDeleteSender && !targetMessage.isOutgoing
    val isValidSender = targetMessage.fromRecipient.id == deleteSenderId || selfIsDeleteSender && targetMessage.isOutgoing

    val messageTimestamp = if (selfIsDeleteSender && targetMessage.isOutgoing) targetMessage.dateSent else targetMessage.serverTimestamp

    return isValidIncomingOutgoing &&
      isValidSender &&
      ((deleteServerTimestamp - messageTimestamp < RECEIVE_THRESHOLD) || (selfIsDeleteSender && targetMessage.isOutgoing))
  }

  @JvmStatic
  fun isValidEditMessageReceive(targetMessage: MessageRecord, editSender: Recipient, editServerTimestamp: Long): Boolean {
    return isValidRemoteDeleteReceive(targetMessage, editSender.id, editServerTimestamp)
  }

  @JvmStatic
  fun isValidRemoteDeleteSend(targetMessages: Collection<MessageRecord>, currentTime: Long): Boolean {
    // TODO [greyson] [remote-delete] Update with server timestamp when available for outgoing messages
    return targetMessages.all { isValidRemoteDeleteSend(it, currentTime) }
  }

  @JvmStatic
  fun getEditMessageThresholdHours(): Int {
    return SEND_THRESHOLD.hours.inWholeHours.toInt()
  }

  /**
   * Check if at the current time a target message can be edited
   */
  @JvmStatic
  fun isValidEditMessageSend(targetMessage: MessageRecord, currentTime: Long): Boolean {
    return isValidRemoteDeleteSend(targetMessage, currentTime) &&
      targetMessage.revisionNumber < 10 &&
      !targetMessage.isViewOnceMessage() &&
      !targetMessage.hasAudio() &&
      !targetMessage.hasSharedContact()
  }

  /**
   * Check regardless of timing, whether a target message can be edited
   */
  @JvmStatic
  fun isValidEditMessageSend(targetMessage: MessageRecord): Boolean {
    return isValidEditMessageSend(targetMessage, targetMessage.dateSent)
  }

  private fun isValidRemoteDeleteSend(message: MessageRecord, currentTime: Long): Boolean {
    return !message.isUpdate &&
      message.isOutgoing &&
      message.isPush &&
      (!message.toRecipient.isGroup || message.toRecipient.isActiveGroup) &&
      !message.isRemoteDelete &&
      !message.hasGiftBadge() &&
      !message.isPaymentNotification &&
      (currentTime - message.dateSent < SEND_THRESHOLD || message.toRecipient.isSelf)
  }

  private fun isSelf(recipientId: RecipientId): Boolean {
    return Recipient.isSelfSet() && Recipient.self().id == recipientId
  }
}
