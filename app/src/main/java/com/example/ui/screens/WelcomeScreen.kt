package com.example.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.EmisBlueContainer
import com.example.ui.theme.EmisBlueDark
import com.example.ui.theme.EmisBlueLight
import com.example.ui.theme.EmisBluePrimary
import com.example.ui.theme.EmisCardBorder
import com.example.ui.theme.EmisSuccess
import com.example.viewmodel.EmisViewModel

@Composable
fun WelcomeScreen(viewModel: EmisViewModel) {
    val teacher by viewModel.currentTeacher.collectAsState()
    val isComplete by viewModel.isWelcomeSyncComplete.collectAsState()
    val statusMessage by viewModel.welcomeStatusMessage.collectAsState()
    val syncProgressMessage by viewModel.syncMessage.collectAsState()
    val syncPercentage by viewModel.syncPercentage.collectAsState()

    val animatedProgress by animateFloatAsState(
        targetValue = if (isComplete) 1f else (syncPercentage.coerceIn(15, 95) / 100f),
        animationSpec = tween(durationMillis = 500),
        label = "syncProgressAnimation"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFF0F6FA),
                        Color(0xFFE8F1F6),
                        MaterialTheme.colorScheme.background
                    )
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("welcome_screen"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 460.dp)
                .fillMaxWidth()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Teacher Avatar Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("welcome_card"),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, EmisCardBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // School and Teacher Icon Avatar
                    Box(
                        modifier = Modifier
                            .size(90.dp)
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(EmisBluePrimary, EmisBlueDark)
                                ),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.School,
                            contentDescription = "المعلم",
                            tint = Color.White,
                            modifier = Modifier.size(50.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Text(
                        text = "مرحباً بك مجدداً",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Prominent Teacher Name
                    Text(
                        text = teacher?.name ?: "الأستاذ(ة)",
                        style = MaterialTheme.typography.headlineSmall,
                        fontSize = 23.sp,
                        fontWeight = FontWeight.Bold,
                        color = EmisBlueDark,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.testTag("welcome_teacher_name")
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Teacher Role Badge (if Admin)
                    if (teacher?.isAdmin == true) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFFFEF3C7)
                        ) {
                            Text(
                                text = "إدارة المدرسة",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF92400E),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(20.dp))
                    } else {
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // Sync & Refresh Status Box with Percentage Loading Bar
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = if (isComplete) Color(0xFFECFDF5) else Color(0xFFF8FAFC),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = 1.dp,
                                color = if (isComplete) EmisSuccess.copy(alpha = 0.4f) else EmisCardBorder,
                                shape = RoundedCornerShape(16.dp)
                            )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (!isComplete) {
                                // Percentage Header
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${(animatedProgress * 100).toInt()}%",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = EmisBluePrimary
                                    )
                                    Text(
                                        text = "جاري التحميل والمزامنة",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color(0xFF64748B),
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // Percentage Loading Bar
                                LinearProgressIndicator(
                                    progress = { animatedProgress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(10.dp)
                                        .clip(RoundedCornerShape(5.dp)),
                                    color = EmisBluePrimary,
                                    trackColor = Color(0xFFE2E8F0)
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                Text(
                                    text = syncProgressMessage ?: statusMessage,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = EmisBlueDark,
                                    fontWeight = FontWeight.SemiBold,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "جاري مزامنة الشعب والمواد وساعات التعاقد والعلامات...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center
                                )
                            } else {
                                // 100% Complete
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "100%",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = EmisSuccess
                                    )
                                    Text(
                                        text = "اكتملت المزامنة بنجاح",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = EmisSuccess,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                LinearProgressIndicator(
                                    progress = { 1f },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(10.dp)
                                        .clip(RoundedCornerShape(5.dp)),
                                    color = EmisSuccess,
                                    trackColor = Color(0xFFE2E8F0)
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "اكتمل",
                                    tint = EmisSuccess,
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = statusMessage,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = EmisSuccess,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "جاري الدخول إلى التطبيق مباشرة...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
